package moe.momokko.intellido.platform.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

class IntelliDoLocaleTest {
    @Test
    fun `first launch prefers simplified Chinese regardless of OS locale`() {
        val store = InMemoryLocalPreferenceStore()
        val locale = IntelliDoLocale(store).resolve(Locale.US)
        assertEquals(Locale.SIMPLIFIED_CHINESE, locale)
        assertEquals("zh-CN", store.get(IntelliDoLocale.PREFERENCE_KEY))
    }

    @Test
    fun `later launches keep the saved locale`() {
        val store = InMemoryLocalPreferenceStore()
        store.set(IntelliDoLocale.PREFERENCE_KEY, "en")
        val locale = IntelliDoLocale(store).resolve(Locale.SIMPLIFIED_CHINESE)
        assertEquals(Locale.forLanguageTag("en"), locale)
    }

    @Test
    fun `missing Chinese string falls back to English`() {
        val text = IntelliDoStrings.message("test.english.only", Locale.SIMPLIFIED_CHINESE)
        assertEquals("English only", text)
    }

    @Test
    fun `Chinese bundle provides Home and unofficial terminology`() {
        val zh = Locale.SIMPLIFIED_CHINESE
        assertEquals("Home", IntelliDoStrings.message("tab.home", zh))
        assertEquals("欢迎", IntelliDoStrings.message("tab.welcome", zh))
        assertEquals("非官方 LINUX DO 客户端", IntelliDoStrings.message("product.unofficial", zh))
        assertEquals("登录", IntelliDoStrings.message("action.signIn", zh))
        assertEquals("分类", IntelliDoStrings.message("directory.categories", zh))
        assertEquals("搜索", IntelliDoStrings.message("search.public", zh))
        assertEquals("查看 1 个新的或更新的话题", IntelliDoStrings.message("home.incoming", zh, 1))
        assertEquals("正在回复…", IntelliDoStrings.message("topic.replying", zh))
        assertEquals("复制链接", IntelliDoStrings.message("topic.copyLink", zh))
        assertEquals("查看 2 个新帖子", IntelliDoStrings.message("topic.newPosts", zh, 2))
        assertEquals("清除所有本地数据", IntelliDoStrings.message("reset.title", zh))
        assertEquals("退出登录", IntelliDoStrings.message("action.signOut", zh))
        assertEquals("登录", IntelliDoStrings.message("account.placeholder", zh))
        assertEquals("本切片尚未接通", IntelliDoStrings.message("toolwindow.notInSlice", zh))
    }
}
