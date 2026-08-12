package com.nancheung.plugins.jetbrains.legadoreader.storage.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.Service;
import com.nancheung.plugins.jetbrains.legadoreader.crypto.AesCryptoUtil;
import com.nancheung.plugins.jetbrains.legadoreader.storage.PluginSettingsStorage;
import com.nancheung.plugins.jetbrains.legadoreader.storage.cache.dto.BookCacheMeta;
import com.nancheung.plugins.jetbrains.legadoreader.storage.cache.dto.BookCacheProgress;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 书籍缓存存储服务（Application Service）
 * <p>
 * 负责将缓存数据 AES 加密后落盘，目录结构：
 * <pre>
 * &lt;configPath&gt;/legado-reader-cache/
 *   book_&lt;md5(bookUrl)&gt;/
 *     meta.enc            # 加密的书籍元数据（含章节列表）
 *     progress.enc        # 加密的缓存进度位图
 *     chapters/
 *       0.enc             # 加密的章节内容
 *       1.enc
 *       ...
 * </pre>
 * 所有文件均使用 AES-256-CBC 加密，IV 内嵌于文件头部。
 *
 * @author NanCheung
 */
@Slf4j
@Service
public final class BookCacheStorage {

    /**
     * 缓存根目录子名
     */
    private static final String CACHE_DIR_NAME = "legado-reader-cache";

    /**
     * 章节子目录名
     */
    private static final String CHAPTERS_DIR = "chapters";

    /**
     * 元数据文件名
     */
    private static final String META_FILE = "meta.enc";

    /**
     * 进度文件名
     */
    private static final String PROGRESS_FILE = "progress.enc";

    /**
     * 章节文件后缀
     */
    private static final String CHAPTER_FILE_SUFFIX = ".enc";

    /**
     * Jackson 单例（线程安全）
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * 获取单例实例
     */
    public static BookCacheStorage getInstance() {
        return ApplicationManager.getApplication().getService(BookCacheStorage.class);
    }

    // ==================== 路径工具 ====================

    /**
     * 获取缓存根目录
     */
    public Path getCacheRoot() {
        return Paths.get(PathManager.getConfigPath(), CACHE_DIR_NAME);
    }

    /**
     * 获取某本书的缓存目录
     *
     * @param bookUrl 书籍 URL
     * @return 缓存目录路径
     */
    public Path getBookDir(String bookUrl) {
        return getCacheRoot().resolve(bookKey(bookUrl));
    }

    /**
     * 获取章节文件路径
     *
     * @param bookUrl 书籍 URL
     * @param index   章节索引
     * @return 章节文件路径
     */
    private Path getChapterFile(String bookUrl, int index) {
        return getBookDir(bookUrl).resolve(CHAPTERS_DIR).resolve(index + CHAPTER_FILE_SUFFIX);
    }

    /**
     * 计算书籍缓存键（MD5 哈希，避免路径非法字符）
     *
     * @param bookUrl 书籍 URL
     * @return 缓存键（如 {@code book_1a2b3c4d5e6f7788}）
     */
    public static String bookKey(String bookUrl) {
        if (bookUrl == null || bookUrl.isEmpty()) {
            return "book_unknown";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(bookUrl.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("book_");
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // MD5 在所有 JDK 中均存在，理论不会到这里
            return "book_" + Integer.toHexString(bookUrl.hashCode());
        }
    }

    /**
     * 从 AES 密钥缓存中读取密钥
     * 若设置中未存储密钥，则自动生成并保存
     *
     * @return AES 密钥字节数组；若缓存禁用则返回 null
     */
    private byte[] getKey() {
        PluginSettingsStorage.State state = PluginSettingsStorage.getInstance().getState();
        if (state == null || !Boolean.TRUE.equals(state.cacheEnabled)) {
            return null;
        }
        String keyStr = state.cacheKey;
        if (keyStr == null || keyStr.isEmpty()) {
            // 自动生成密钥并持久化
            byte[] newKey = AesCryptoUtil.generateKey();
            state.cacheKey = AesCryptoUtil.encodeKey(newKey);
            return newKey;
        }
        try {
            return AesCryptoUtil.decodeKey(keyStr);
        } catch (IllegalArgumentException e) {
            log.warn("缓存密钥损坏，重新生成：{}", e.getMessage());
            byte[] newKey = AesCryptoUtil.generateKey();
            state.cacheKey = AesCryptoUtil.encodeKey(newKey);
            return newKey;
        }
    }

    /**
     * 缓存功能是否启用
     */
    public boolean isCacheEnabled() {
        PluginSettingsStorage.State state = PluginSettingsStorage.getInstance().getState();
        return state != null && Boolean.TRUE.equals(state.cacheEnabled);
    }

    // ==================== 元数据读写 ====================

    /**
     * 保存书籍元数据
     *
     * @param meta 元数据
     */
    public void saveMeta(BookCacheMeta meta) {
        byte[] key = getKey();
        if (key == null) {
            log.debug("缓存未启用，跳过保存元数据");
            return;
        }
        try {
            Path dir = getBookDir(meta.getBookUrl());
            Files.createDirectories(dir);
            String json = MAPPER.writeValueAsString(meta);
            byte[] encrypted = AesCryptoUtil.encryptString(json, key);
            Files.write(dir.resolve(META_FILE), encrypted);
            log.debug("已保存元数据：book={}, chapters={}", meta.getBookUrl(), meta.getTotalChapters());
        } catch (IOException e) {
            log.error("保存元数据失败：book={}", meta.getBookUrl(), e);
        }
    }

    /**
     * 读取书籍元数据
     *
     * @param bookUrl 书籍 URL
     * @return 元数据；不存在或读取失败则返回 null
     */
    public BookCacheMeta loadMeta(String bookUrl) {
        if (bookUrl == null) {
            return null;
        }
        byte[] key = getKey();
        if (key == null) {
            return null;
        }
        Path file = getBookDir(bookUrl).resolve(META_FILE);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            byte[] encrypted = Files.readAllBytes(file);
            String json = AesCryptoUtil.decryptToString(encrypted, key);
            return MAPPER.readValue(json, BookCacheMeta.class);
        } catch (Exception e) {
            log.warn("读取元数据失败：book={}", bookUrl, e);
            return null;
        }
    }

    // ==================== 章节读写 ====================

    /**
     * 保存单章内容（加密）
     *
     * @param bookUrl 书籍 URL
     * @param index   章节索引
     * @param content 正文内容
     */
    public void saveChapter(String bookUrl, int index, String content) {
        byte[] key = getKey();
        if (key == null) {
            return;
        }
        try {
            Path chaptersDir = getBookDir(bookUrl).resolve(CHAPTERS_DIR);
            Files.createDirectories(chaptersDir);
            byte[] encrypted = AesCryptoUtil.encryptString(content, key);
            Files.write(getChapterFile(bookUrl, index), encrypted);
            log.debug("已缓存章节：book={}, index={}", bookUrl, index);
        } catch (IOException e) {
            log.error("保存章节失败：book={}, index={}", bookUrl, index, e);
        }
    }

    /**
     * 读取单章内容（解密）
     *
     * @param bookUrl 书籍 URL
     * @param index   章节索引
     * @return 正文内容；不存在或读取失败则返回 null
     */
    public String loadChapter(String bookUrl, int index) {
        if (bookUrl == null) {
            return null;
        }
        byte[] key = getKey();
        if (key == null) {
            return null;
        }
        Path file = getChapterFile(bookUrl, index);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            byte[] encrypted = Files.readAllBytes(file);
            return AesCryptoUtil.decryptToString(encrypted, key);
        } catch (Exception e) {
            log.warn("读取章节失败：book={}, index={}", bookUrl, index, e);
            return null;
        }
    }

    /**
     * 是否已缓存某章节
     *
     * @param bookUrl 书籍 URL
     * @param index   章节索引
     * @return true 如果已缓存
     */
    public boolean hasChapter(String bookUrl, int index) {
        if (bookUrl == null) {
            return false;
        }
        return Files.exists(getChapterFile(bookUrl, index));
    }

    /**
     * 列出某本书已缓存的所有章节索引
     *
     * @param bookUrl 书籍 URL
     * @return 章节索引集合（有序）
     */
    public Set<Integer> listCachedChapterIndexes(String bookUrl) {
        if (bookUrl == null) {
            return Collections.emptySet();
        }
        Path chaptersDir = getBookDir(bookUrl).resolve(CHAPTERS_DIR);
        if (!Files.exists(chaptersDir)) {
            return Collections.emptySet();
        }
        try (Stream<Path> stream = Files.list(chaptersDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.endsWith(CHAPTER_FILE_SUFFIX))
                    .map(name -> name.substring(0, name.length() - CHAPTER_FILE_SUFFIX.length()))
                    .flatMap(name -> {
                        try {
                            return Stream.of(Integer.parseInt(name));
                        } catch (NumberFormatException e) {
                            return Stream.empty();
                        }
                    })
                    .collect(Collectors.toCollection(TreeSet::new));
        } catch (IOException e) {
            log.warn("列出章节索引失败：book={}", bookUrl, e);
            return Collections.emptySet();
        }
    }

    // ==================== 缓存进度读写 ====================

    /**
     * 保存缓存进度
     *
     * @param progress 缓存进度
     */
    public void saveProgress(BookCacheProgress progress) {
        byte[] key = getKey();
        if (key == null) {
            return;
        }
        try {
            Path dir = getBookDir(progress.getBookUrl());
            Files.createDirectories(dir);
            String json = MAPPER.writeValueAsString(progress);
            byte[] encrypted = AesCryptoUtil.encryptString(json, key);
            Files.write(dir.resolve(PROGRESS_FILE), encrypted);
        } catch (IOException e) {
            log.error("保存进度失败：book={}", progress.getBookUrl(), e);
        }
    }

    /**
     * 读取缓存进度
     *
     * @param bookUrl 书籍 URL
     * @return 进度；不存在则返回 null
     */
    public BookCacheProgress loadProgress(String bookUrl) {
        if (bookUrl == null) {
            return null;
        }
        byte[] key = getKey();
        if (key == null) {
            return null;
        }
        Path file = getBookDir(bookUrl).resolve(PROGRESS_FILE);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            byte[] encrypted = Files.readAllBytes(file);
            String json = AesCryptoUtil.decryptToString(encrypted, key);
            return MAPPER.readValue(json, BookCacheProgress.class);
        } catch (Exception e) {
            log.warn("读取进度失败：book={}", bookUrl, e);
            return null;
        }
    }

    // ==================== 删除与清理 ====================

    /**
     * 删除某本书的全部缓存
     *
     * @param bookUrl 书籍 URL
     */
    public void deleteBookCache(String bookUrl) {
        if (bookUrl == null) {
            return;
        }
        Path dir = getBookDir(bookUrl);
        if (!Files.exists(dir)) {
            return;
        }
        deleteRecursively(dir);
        log.info("已删除缓存：book={}", bookUrl);
    }

    /**
     * 清空全部缓存
     */
    public void clearAll() {
        Path root = getCacheRoot();
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(Files::isDirectory).forEach(this::deleteRecursively);
        } catch (IOException e) {
            log.error("清空缓存失败", e);
        }
        log.info("已清空全部缓存");
    }

    /**
     * 递归删除目录
     */
    private void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    Files.delete(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("递归删除目录失败：{}", dir, e);
        }
    }

    /**
     * 列出所有已缓存书籍的元数据
     *
     * @return 元数据列表（按缓存时间倒序）
     */
    public List<BookCacheMeta> listCachedMetas() {
        Path root = getCacheRoot();
        if (!Files.exists(root)) {
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.list(root)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(this::loadMetaByDir)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingLong(BookCacheMeta::getCachedAt).reversed())
                    .toList();
        } catch (IOException e) {
            log.error("列出缓存书籍失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 列出所有已缓存书籍的 URL
     *
     * @return 书籍 URL 列表（按缓存时间倒序）
     */
    public List<String> listCachedBooks() {
        return listCachedMetas().stream()
                .map(BookCacheMeta::getBookUrl)
                .toList();
    }

    /**
     * 通过目录路径加载元数据（内部使用，避免重复 bookKey 计算）
     */
    private BookCacheMeta loadMetaByDir(Path bookDir) {
        byte[] key = getKey();
        if (key == null) {
            return null;
        }
        Path file = bookDir.resolve(META_FILE);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            byte[] encrypted = Files.readAllBytes(file);
            String json = AesCryptoUtil.decryptToString(encrypted, key);
            return MAPPER.readValue(json, BookCacheMeta.class);
        } catch (Exception e) {
            log.warn("读取元数据失败：dir={}", bookDir, e);
            return null;
        }
    }

    /**
     * 获取缓存目录占用大小（字节）
     */
    public long getCacheSize() {
        Path root = getCacheRoot();
        if (!Files.exists(root)) {
            return 0;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .mapToLong(file -> {
                        try {
                            return Files.size(file);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            log.error("计算缓存大小失败", e);
            return 0;
        }
    }

    // ==================== 便捷查询 ====================

    /**
     * 是否已缓存完整书籍
     *
     * @param bookUrl       书籍 URL
     * @param totalChapters 总章节数
     * @return true 如果全部章节已缓存
     */
    public boolean isBookComplete(String bookUrl, int totalChapters) {
        if (bookUrl == null || totalChapters <= 0) {
            return false;
        }
        BookCacheProgress progress = loadProgress(bookUrl);
        if (progress != null && BookCacheProgress.STATUS_COMPLETE.equals(progress.getStatus())) {
            return true;
        }
        Set<Integer> cached = listCachedChapterIndexes(bookUrl);
        if (cached.size() < totalChapters) {
            return false;
        }
        for (int i = 0; i < totalChapters; i++) {
            if (!cached.contains(i)) {
                return false;
            }
        }
        return true;
    }
}
