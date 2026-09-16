package com.zhou.kebiao.parser

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.InputStream

/**
 * PDF → TextFragment 列表。
 *
 * 设计说明：这是全项目唯一依赖 PDFBox 的文件。算法逻辑不写在这里，
 * 而是放到 TimetableParser —— 那样才能用 JVM 单元测试覆盖（PDFBox 需要 Android 运行时）。
 *
 * 粒度：[PDFTextStripper.writeString] 每个「词」回调一次（writeLine 逐个 WordWithTextPositions 调用）。
 * 本样本是无空格的中文，一次回调恰好对应表格里的一行。不逐字形产出，是因为
 * 页底「其他课程」那一行横跨近 800pt，拆成逐字形后右半段会落进星期列，污染网格。
 *
 * 坐标：`xDirAdj` / `yDirAdj` 是 PDFBox 按**文字方向**（[TextPosition.getDir]）校正过的视觉坐标
 * （x 向右、y 向下、原点在视觉页左上）。样本 PDF 的文字方向是 90°、页面 /Rotate 也是 90°，
 * 二者抵消，所以这两个值**已经是**视觉坐标，不能再按 /Rotate 变换一次 ——
 * 再转 90° 会把 7 个星期列压成同一列，整个解析直接失效。
 * 换模板时若文字方向与 /Rotate 不一致，需在此补一次 (Rotate − dir) 的旋转。
 */
class PdfTextExtractor(private val context: Context) {

    init {
        // PDFBox-Android 需要在首次使用前初始化资源加载器
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    fun extract(input: InputStream): List<TextFragment> {
        val fragments = mutableListOf<TextFragment>()
        PDDocument.load(input).use { doc ->
            val stripper = object : PDFTextStripper() {
                override fun writeString(text: String, textPositions: List<TextPosition>) {
                    val visible = text.trim()
                    if (visible.isEmpty() || textPositions.isEmpty()) return
                    // currentPageNo 是 protected，在这里（子类内部）才拿得到；
                    // PDFBox 的页号是 1 起，转成 0 起以对齐 PDF 页序。
                    fragments += toFragment(currentPageNo - 1, textPositions, visible)
                }
            }
            stripper.sortByPosition = true
            stripper.startPage = 1
            stripper.endPage = doc.numberOfPages
            stripper.getText(doc)
        }
        return fragments
    }

    /**
     * 把一次 writeString 回调里的字形合成一个文字块：
     * x 取最左字形的左边缘，y 取最高字形的行顶，字号取这段里最大的。
     */
    private fun toFragment(
        pageIndex: Int,
        positions: List<TextPosition>,
        text: String,
    ): TextFragment {
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var fontSize = 0f

        for (tp in positions) {
            if (tp.xDirAdj < left) left = tp.xDirAdj
            val lineTop = tp.yDirAdj - ascentOf(tp)
            if (lineTop < top) top = lineTop
            if (tp.fontSizeInPt > fontSize) fontSize = tp.fontSizeInPt
        }

        return TextFragment(pageIndex, left, top, fontSize, text)
    }

    /**
     * 行顶到基线的距离。PDFBox 只给基线（yDirAdj），给出行顶要靠字体的 ascent
     * （样本字体 STSong-Light 是 880/1000）。
     * 字号取最大：同一行里课程名与正文的字号必然不同，但那是不同的回调，不会混在一起。
     */
    private fun ascentOf(tp: TextPosition): Float {
        val ascent = tp.font?.fontDescriptor?.ascent ?: 0f
        return if (ascent > 0f) ascent / 1000f * tp.fontSizeInPt else tp.heightDir
    }
}
