package com.nancheung.plugins.jetbrains.legadoreader.api;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.nancheung.plugins.jetbrains.legadoreader.api.dto.BookChapterDTO;
import com.nancheung.plugins.jetbrains.legadoreader.api.dto.BookDTO;
import com.nancheung.plugins.jetbrains.legadoreader.api.dto.BookProgressDTO;
import com.nancheung.plugins.jetbrains.legadoreader.api.dto.R;
import com.nancheung.plugins.jetbrains.legadoreader.manager.ReadingSessionManager;
import com.nancheung.plugins.jetbrains.legadoreader.storage.AddressHistoryStorage;
import com.nancheung.plugins.jetbrains.legadoreader.storage.PluginSettingsStorage;
import lombok.experimental.UtilityClass;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * API 工具
 *
 * @author NanCheung
 */
@UtilityClass
public class ApiUtil {

    /**
     * 获取书架目录列表
     *
     * @return 书架目录列表
     */
    public List<BookDTO> getBookshelf() {
        String url = AddressHistoryStorage.getInstance().getMostRecent() + AddressEnum.GET_BOOKSHELF.getAddress();

        R<List<BookDTO>> r = get(url, new TypeReference<>() {
        });

        return r.getData();
    }

    /**
     * 获取正文内容
     *
     * @return 正文内容
     */
    public String getBookContent(String bookUrl, int bookIndex) {
        // 调用 API获取正文内容
        String url = AddressHistoryStorage.getInstance().getMostRecent() + AddressEnum.GET_BOOK_CONTENT.getAddress() + "?url=" + URLUtil.encodeAll(bookUrl) + "&index=" + bookIndex;

        R<String> r = get(url, new TypeReference<>() {
        });

        return r.getData();
    }

    /**
     * 获取章节目录列表
     *
     * @return 章节目录列表
     */
    public List<BookChapterDTO> getChapterList(String bookUrl) {
        // 调用 API获取书架目录
        String url = AddressHistoryStorage.getInstance().getMostRecent() + AddressEnum.GET_CHAPTER_LIST.getAddress() + "?url=" + URLUtil.encodeAll(bookUrl);

        R<List<BookChapterDTO>> r = get(url, new TypeReference<>() {
        });

        return r.getData();
    }

    /**
     * 保存阅读进度
     */
    public void saveBookProgress(String author, String name, int index, String title, int durChapterPos) {
        String url = AddressHistoryStorage.getInstance().getMostRecent() + AddressEnum.SAVE_BOOK_PROGRESS.getAddress();

        BookProgressDTO bookProgressDTO = BookProgressDTO.builder()
                .author(author)
                .name(name)
                .durChapterIndex(index)
                .durChapterTitle(title)
                .durChapterTime(System.currentTimeMillis())
                .durChapterPos(durChapterPos)
                .url(ReadingSessionManager.getInstance().getCurrentBook().getBookUrl())
                .index(index)
                .build();

        // 使用表单方式发送，确保自定义参数（如 accessToken）和进度数据一起提交
        saveProgressAsForm(url, bookProgressDTO);
    }

    /**
     * 以表单方式保存阅读进度，将自定义参数和进度数据合并为一个表单请求
     * 解决 Hutool 中 .body() 覆盖 .form() 导致自定义参数丢失的问题
     */
    private void saveProgressAsForm(String url, BookProgressDTO progressDTO) {
        Map<String, Object> formParams = new LinkedHashMap<>(parseCustomParams());
        // 将进度数据添加到表单参数中
        formParams.put("name", progressDTO.getName());
        formParams.put("author", progressDTO.getAuthor());
        formParams.put("durChapterIndex", String.valueOf(progressDTO.getDurChapterIndex()));
        formParams.put("durChapterTitle", progressDTO.getDurChapterTitle());
        formParams.put("durChapterTime", String.valueOf(progressDTO.getDurChapterTime()));
        formParams.put("durChapterPos", String.valueOf(progressDTO.getDurChapterPos()));
        if (progressDTO.getUrl() != null) {
            formParams.put("url", progressDTO.getUrl());
        }
        if (progressDTO.getIndex() != null) {
            formParams.put("index", String.valueOf(progressDTO.getIndex()));
        }

        try {
            HttpUtil.createPost(url)
                    .form(formParams)
                    .execute();
        } catch (Exception e) {
            throw new RuntimeException(String.format("\n%s：%s\n参数：\n%s\n", "保存阅读进度失败", url, formParams), e);
        }
    }


    private <R> R get(String url, TypeReference<R> typeReference) {
        String textBody;

        try {
            textBody = HttpUtil.get(url, parseCustomParams());
        } catch (Exception e) {
            throw new RuntimeException(String.format("\n%s：%s\n参数：\n%s\n", "调用API失败", url, parseCustomParams()), e);
        }

        return JSONUtil.toBean(textBody, typeReference, true);
    }

    private <R> R post(String url, Object body, TypeReference<R> typeReference) {
        String textBody;
        try (HttpResponse execute = HttpUtil.createPost(url)
                .form(parseCustomParams())
                .body(JSONUtil.toJsonStr(body))
                .execute()) {
            textBody = execute.body();
        } catch (Exception e) {
            throw new RuntimeException(String.format("\n%s：%s\n参数：\n%s\n%s\n", "调用API失败", url, parseCustomParams(), body), e);
        }

        return JSONUtil.toBean(textBody, typeReference, true);
    }

    /**
     * 解析 API 自定义参数
     * 从参数列表中过滤并转换为 Map
     * 允许参数值为空，只过滤空参数名
     *
     * @return 参数 Map
     */
    private static Map<String, Object> parseCustomParams() {
        List<PluginSettingsStorage.CustomParam> params =
                PluginSettingsStorage.getInstance().getState().apiCustomParams;

        return params.stream()
                .filter(p -> p.name != null && !p.name.trim().isEmpty())  // 只过滤空参数名
                .collect(Collectors.toMap(
                        p -> p.name,
                        p -> p.value != null ? p.value : "",  // value 为 null 时使用空字符串
                        (a, b) -> b  // 重复键时保留后者
                ));
    }

}
