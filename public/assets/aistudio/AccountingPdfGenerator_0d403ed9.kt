package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.data.model.AccountingDocument
import com.example.data.model.DocumentLineItem
import com.example.data.model.DocumentType
import com.example.domain.RussianNumberToWords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object AccountingPdfGenerator {

    private const val PAGE_WIDTH = 842
    private const val PAGE_HEIGHT = 595
    private const val MARGIN_X = 28f
    private const val MARGIN_Y = 24f
    private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN_X * 2

    suspend fun generatePdfFile(context: Context, doc: AccountingDocument): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "docs").apply { mkdirs() }
            val safeNumber = doc.docNumber.replace(Regex("[^a-zA-Z0-9а-яА-Я_-]"), "_")
            val prefix = if (doc.resolvedType == DocumentType.INVOICE) "SchetFaktura" else "TORG12"
            val outFile = File(dir, "${prefix}_${safeNumber}_${doc.id.take(6)}.pdf")

            val pdf = PdfDocument()
            try {
                if (doc.resolvedType == DocumentType.INVOICE) {
                    drawInvoicePages(pdf, doc)
                } else {
                    drawTorg12Pages(pdf, doc)
                }
                FileOutputStream(outFile).use { fos ->
                    pdf.writeTo(fos)
                }
            } finally {
                pdf.close()
            }
            outFile
        }

    suspend fun renderPreviewPages(file: File): List<Bitmap> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Bitmap>()
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        try {
            val scale = 2
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                try {
                    val bitmap = Bitmap.createBitmap(
                        page.width * scale,
                        page.height * scale,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    result.add(bitmap)
                } finally {
                    page.close()
                }
            }
        } finally {
            renderer.close()
            pfd.close()
        }
        result
    }

    private fun drawInvoicePages(pdf: PdfDocument, doc: AccountingDocument) {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 7.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val boldPaint = TextPaint(textPaint).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val titlePaint = TextPaint(boldPaint).apply {
            textSize = 11f
        }
        val smallPaint = TextPaint(textPaint).apply {
            textSize = 6.3f
        }
        val linePaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 0.6f
            style = Paint.Style.STROKE
        }

        val colWidths = floatArrayOf(
            20f,  // 1: №
            162f, // 1а: Наименование
            36f,  // 1б: Код вида
            30f,  // 2: Код ед.
            36f,  // 2а: Ед. изм.
            40f,  // 3: Кол-во
            54f,  // 4: Цена
            68f,  // 5: Стоимость без НДС
            36f,  // 6: Акциз
            42f,  // 7: Ставка
            60f,  // 8: Сумма НДС
            72f,  // 9: Стоимость с НДС
            30f,  // 10: Код страны
            54f,  // 10а: Страна
            46f   // 11: ДТ
        )
        val colHeaders = listOf(
            "№\nп/п",
            "Наименование товара (описание выполненных работ, оказанных услуг), имущественного права",
            "Код вида товара",
            "Код\nед. изм.",
            "Усл.\nобозн.",
            "Коли-\nчество\n(объем)",
            "Цена\n(тариф) за\nед. изм.",
            "Стоимость товаров\n(работ, услуг) без\nналога — всего",
            "В том\nчисле\nсумма\nакциза",
            "Налого-\nвая\nставка",
            "Сумма\nналога,\nпредъявл.\nпокупат.",
            "Стоимость товаров\n(работ, услуг) с\nналогом — всего",
            "Цифр.\nкод",
            "Краткое\nнаимен.\nстраны",
            "Рег. номер\nдекларации\nна товары"
        )
        val colNumbers = listOf(
            "1", "1а", "1б", "2", "2а", "3", "4", "5", "6", "7", "8", "9", "10", "10а", "11"
        )

        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN_Y

        // Top right form notice
        drawRightAlignedText(
            canvas,
            "Приложение № 1 к постановлению Правительства Российской Федерации от 26 декабря 2011 г. № 1137",
            PAGE_WIDTH - MARGIN_X,
            y,
            smallPaint
        )
        y += 14f

        canvas.drawText(
            "СЧЁТ-ФАКТУРА № ${doc.docNumber} от ${doc.docDate}",
            MARGIN_X,
            y,
            titlePaint
        )
        y += 12f
        canvas.drawText("Исправление № -- от --", MARGIN_X, y, textPaint)
        y += 12f

        val sellerInnKpp = formatInnKpp(doc.sellerInn, doc.sellerKpp)
        val buyerInnKpp = formatInnKpp(doc.buyerInn, doc.buyerKpp)
        val consignor = if (doc.consignorInfo.equals("Он же", ignoreCase = true) || doc.consignorInfo.isBlank()) {
            "Он же"
        } else {
            doc.consignorInfo
        }
        val consignee = if (doc.consigneeInfo.equals("Он же", ignoreCase = true) || doc.consigneeInfo.isBlank()) {
            "${doc.buyerName}, ${doc.buyerAddress}"
        } else {
            doc.consigneeInfo
        }

        val headerLines = listOf(
            "Продавец: ${doc.sellerName} (2)",
            "Адрес: ${doc.sellerAddress} (2а)",
            "ИНН/КПП продавца: $sellerInnKpp (2б)",
            "Грузоотправитель и его адрес: $consignor (3)",
            "Грузополучатель и его адрес: $consignee (4)",
            "К платёжно-расчётному документу № ${doc.paymentDoc.ifBlank { "--" }} (5)",
            "Документ об отгрузке: ${doc.basisDoc.ifBlank { "--" }} (5а)",
            "Покупатель: ${doc.buyerName} (6)",
            "Адрес: ${doc.buyerAddress} (6а)",
            "ИНН/КПП покупателя: $buyerInnKpp (6б)",
            "Валюта: наименование, код Российский рубль, 643 (7)",
            "Идентификатор государственного контракта, договора (соглашения): -- (8)"
        )
        for (line in headerLines) {
            y += drawWrappedLine(canvas, line, MARGIN_X, y, CONTENT_WIDTH, textPaint)
            y += 2f
        }
        y += 6f

        y = drawInvoiceTableHeader(canvas, y, colWidths, colHeaders, colNumbers, smallPaint, boldPaint, linePaint)

        doc.items.forEachIndexed { index, item ->
            val cells = invoiceRowCells(index + 1, item)
            val rowHeight = measureRowHeight(cells, colWidths, smallPaint, minHeight = 16f)
            if (y + rowHeight > PAGE_HEIGHT - 85f) {
                pdf.finishPage(page)
                pageNumber++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = MARGIN_Y
                canvas.drawText(
                    "Счёт-фактура № ${doc.docNumber} от ${doc.docDate} (лист $pageNumber)",
                    MARGIN_X,
                    y + 8f,
                    boldPaint
                )
                y += 16f
                y = drawInvoiceTableHeader(canvas, y, colWidths, colHeaders, colNumbers, smallPaint, boldPaint, linePaint)
            }
            drawTableRow(canvas, y, rowHeight, cells, colWidths, smallPaint, linePaint, numericCols = setOf(0, 5, 6, 7, 10, 11))
            y += rowHeight
        }

        // Totals row
        val totalCells = listOf(
            "",
            "Всего к оплате (9)",
            "", "", "", "", "X",
            RussianNumberToWords.formatMoney(doc.subtotalWithoutVat),
            "без акциза",
            "X",
            RussianNumberToWords.formatMoney(doc.totalVat),
            RussianNumberToWords.formatMoney(doc.totalWithVat),
            "", "", ""
        )
        val totalH = 18f
        drawTableRow(canvas, y, totalH, totalCells, colWidths, boldPaint, linePaint, numericCols = setOf(7, 10, 11))
        y += totalH + 18f

        if (y + 52f > PAGE_HEIGHT - MARGIN_Y) {
            pdf.finishPage(page)
            pageNumber++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = MARGIN_Y + 12f
        }

        val leftX = MARGIN_X
        val rightX = MARGIN_X + CONTENT_WIDTH / 2f + 16f
        canvas.drawText("Руководитель организации или иное уполномоченное лицо:", leftX, y, textPaint)
        canvas.drawText("Главный бухгалтер или иное уполномоченное лицо:", rightX, y, textPaint)
        y += 16f
        canvas.drawText("_____________________ / ${doc.sellerDirectorName.ifBlank { "________________" }} /", leftX, y, boldPaint)
        canvas.drawText("_____________________ / ${doc.sellerAccountantName.ifBlank { doc.sellerDirectorName.ifBlank { "________________" } }} /", rightX, y, boldPaint)
        y += 10f
        canvas.drawText("         (подпись)                     (Ф.И.О.)", leftX, y, smallPaint)
        canvas.drawText("         (подпись)                     (Ф.И.О.)", rightX, y, smallPaint)

        if (doc.sellerInn.length == 12) {
            y += 16f
            canvas.drawText(
                "Индивидуальный предприниматель _____________________ / ${doc.sellerDirectorName.ifBlank { doc.sellerName }} / (Реквизиты свидетельства ОГРНИП: ${doc.sellerOgrn})",
                leftX,
                y,
                smallPaint
            )
        }

        pdf.finishPage(page)
    }

    private fun drawInvoiceTableHeader(
        canvas: Canvas,
        startY: Float,
        colWidths: FloatArray,
        headers: List<String>,
        colNumbers: List<String>,
        smallPaint: TextPaint,
        boldPaint: TextPaint,
        linePaint: Paint
    ): Float {
        var y = startY
        val headerH = measureRowHeight(headers, colWidths, smallPaint, minHeight = 38f)
        drawTableRow(canvas, y, headerH, headers, colWidths, smallPaint, linePaint, centerAll = true)
        y += headerH
        val numH = 13f
        drawTableRow(canvas, y, numH, colNumbers, colWidths, boldPaint, linePaint, centerAll = true)
        y += numH
        return y
    }

    private fun invoiceRowCells(index: Int, item: DocumentLineItem): List<String> {
        val net = item.totalWithVat - item.vatAmount
        val vatRateStr = when (item.vatRate) {
            -1 -> "Без НДС"
            0 -> "0%"
            else -> "${item.vatRate}%"
        }
        val vatAmtStr = if (item.vatRate < 0) "Без НДС" else RussianNumberToWords.formatMoney(item.vatAmount)
        return listOf(
            index.toString(),
            item.name,
            "-",
            item.unitCode.ifBlank { "-" },
            item.unitName.ifBlank { "шт" },
            RussianNumberToWords.formatQuantity(item.quantity),
            RussianNumberToWords.formatMoney(item.priceWithoutVat),
            RussianNumberToWords.formatMoney(net),
            "без акциза",
            vatRateStr,
            vatAmtStr,
            RussianNumberToWords.formatMoney(item.totalWithVat),
            item.countryCode.ifBlank { "-" },
            item.countryName.ifBlank { "-" },
            item.customsDecl.ifBlank { "-" }
        )
    }

    private fun drawTorg12Pages(pdf: PdfDocument, doc: AccountingDocument) {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 7.3f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val boldPaint = TextPaint(textPaint).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val titlePaint = TextPaint(boldPaint).apply {
            textSize = 10.5f
        }
        val smallPaint = TextPaint(textPaint).apply {
            textSize = 6.2f
        }
        val linePaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 0.6f
            style = Paint.Style.STROKE
        }

        val colWidths = floatArrayOf(
            22f,  // 1: №
            156f, // 2: Наименование
            36f,  // 3: Код
            38f,  // 4: Ед. изм.
            32f,  // 5: Код ОКЕИ
            34f,  // 6: Вид упак.
            34f,  // 7: В одном месте
            34f,  // 8: Мест, штук
            44f,  // 9: Масса брутто
            48f,  // 10: Кол-во нетто
            58f,  // 11: Цена
            68f,  // 12: Сумма без НДС
            40f,  // 13: Ставка НДС
            64f,  // 14: Сумма НДС
            78f   // 15: Сумма с НДС
        )
        val colHeaders = listOf(
            "Но-\nмер\nпо\nпор.",
            "Товар (наименование, характеристика, сорт, артикул товара)",
            "Код",
            "Ед.\nизм.\nнаимен.",
            "Код\nпо\nОКЕИ",
            "Вид\nупа-\nковки",
            "В одном\nместе",
            "Мест,\nштук",
            "Масса\nбрутто",
            "Коли-\nчество\n(масса\nнетто)",
            "Цена,\nруб. коп.",
            "Сумма без\nучета НДС,\nруб. коп.",
            "НДС\nставка,\n%",
            "НДС\nсумма,\nруб. коп.",
            "Сумма с\nучетом НДС,\nруб. коп."
        )
        val colNumbers = (1..15).map { it.toString() }

        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN_Y

        drawRightAlignedText(
            canvas,
            "Унифицированная форма № ТОРГ-12 (утв. постановлением Госкомстата России от 25.12.98 № 132) | ОКУД 0330212",
            PAGE_WIDTH - MARGIN_X,
            y,
            smallPaint
        )
        y += 12f

        val sellerFull = buildPartyRequisitesLine(
            name = doc.sellerName,
            inn = doc.sellerInn,
            kpp = doc.sellerKpp,
            address = doc.sellerAddress,
            phone = doc.sellerPhone,
            bank = doc.sellerBankName,
            bik = doc.sellerBik,
            rs = doc.sellerCheckingAccount,
            ks = doc.sellerCorrAccount
        )
        val buyerFull = buildPartyRequisitesLine(
            name = doc.buyerName,
            inn = doc.buyerInn,
            kpp = doc.buyerKpp,
            address = doc.buyerAddress,
            phone = doc.buyerPhone,
            bank = doc.buyerBankName,
            bik = doc.buyerBik,
            rs = doc.buyerCheckingAccount,
            ks = doc.buyerCorrAccount
        )
        val consignor = if (doc.consignorInfo.equals("Он же", ignoreCase = true) || doc.consignorInfo.isBlank()) {
            sellerFull
        } else {
            doc.consignorInfo
        }
        val consignee = if (doc.consigneeInfo.equals("Он же", ignoreCase = true) || doc.consigneeInfo.isBlank()) {
            buyerFull
        } else {
            doc.consigneeInfo
        }

        val infoLines = listOf(
            "Грузоотправитель: $consignor",
            "Грузополучатель: $consignee",
            "Поставщик: $sellerFull",
            "Плательщик: $buyerFull",
            "Основание: ${doc.basisDoc.ifBlank { "Договор / Счёт" }}"
        )
        for (line in infoLines) {
            y += drawWrappedLine(canvas, line, MARGIN_X, y, CONTENT_WIDTH, textPaint)
            y += 3f
        }
        y += 4f

        // Title box
        val boxW = 320f
        val boxX = (PAGE_WIDTH - boxW) / 2f
        canvas.drawRect(boxX, y, boxX + boxW, y + 26f, linePaint)
        canvas.drawText("ТОВАРНАЯ НАКЛАДНАЯ", boxX + 12f, y + 16f, titlePaint)
        canvas.drawText("№ ${doc.docNumber} от ${doc.docDate}", boxX + 170f, y + 16f, boldPaint)
        y += 34f

        y = drawInvoiceTableHeader(canvas, y, colWidths, colHeaders, colNumbers, smallPaint, boldPaint, linePaint)

        doc.items.forEachIndexed { index, item ->
            val cells = torg12RowCells(index + 1, item, if (index == 0) doc.totalPlaces else 0, if (index == 0) doc.totalWeightKg else 0.0)
            val rowHeight = measureRowHeight(cells, colWidths, smallPaint, minHeight = 16f)
            if (y + rowHeight > PAGE_HEIGHT - 120f) {
                pdf.finishPage(page)
                pageNumber++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = MARGIN_Y
                canvas.drawText(
                    "Товарная накладная № ${doc.docNumber} от ${doc.docDate} (лист $pageNumber)",
                    MARGIN_X,
                    y + 8f,
                    boldPaint
                )
                y += 16f
                y = drawInvoiceTableHeader(canvas, y, colWidths, colHeaders, colNumbers, smallPaint, boldPaint, linePaint)
            }
            drawTableRow(canvas, y, rowHeight, cells, colWidths, smallPaint, linePaint, numericCols = setOf(0, 7, 8, 9, 10, 11, 13, 14))
            y += rowHeight
        }

        val totalCells = listOf(
            "",
            "Всего по накладной",
            "", "", "", "", "",
            doc.totalPlaces.toString(),
            if (doc.totalWeightKg > 0.0) RussianNumberToWords.formatQuantity(doc.totalWeightKg) else "-",
            RussianNumberToWords.formatQuantity(doc.totalQuantity),
            "X",
            RussianNumberToWords.formatMoney(doc.subtotalWithoutVat),
            "X",
            RussianNumberToWords.formatMoney(doc.totalVat),
            RussianNumberToWords.formatMoney(doc.totalWithVat)
        )
        drawTableRow(canvas, y, 18f, totalCells, colWidths, boldPaint, linePaint, numericCols = setOf(7, 8, 9, 11, 13, 14))
        y += 24f

        if (y + 92f > PAGE_HEIGHT - MARGIN_Y) {
            pdf.finishPage(page)
            pageNumber++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = MARGIN_Y + 8f
        }

        val wordsLine = "Всего отпущено ${doc.items.size} наименований на сумму: ${doc.amountInWords}"
        y += drawWrappedLine(canvas, wordsLine, MARGIN_X, y, CONTENT_WIDTH, boldPaint)
        y += 10f

        val leftX = MARGIN_X
        val rightX = MARGIN_X + CONTENT_WIDTH / 2f + 16f
        canvas.drawText("Отпуск груза разрешил: ${doc.sellerDirectorTitle} ________ / ${doc.sellerDirectorName.ifBlank { "____________" }} /", leftX, y, textPaint)
        canvas.drawText("По доверенности № ____________ от ____________", rightX, y, textPaint)
        y += 16f
        canvas.drawText("Главный (старший) бухгалтер: ________ / ${doc.sellerAccountantName.ifBlank { doc.sellerDirectorName.ifBlank { "____________" } }} /", leftX, y, textPaint)
        canvas.drawText("Груз принял: ________________________ / ____________ /", rightX, y, textPaint)
        y += 16f
        canvas.drawText("Отпуск груза произвёл: ________________ / ____________ /", leftX, y, textPaint)
        canvas.drawText("Груз получил грузополучатель: _________ / ____________ /", rightX, y, textPaint)
        y += 14f
        canvas.drawText("М.П.   \"___\" ____________ 20__ г.", leftX, y, smallPaint)
        canvas.drawText("М.П.   \"___\" ____________ 20__ г.", rightX, y, smallPaint)

        pdf.finishPage(page)
    }

    private fun torg12RowCells(
        index: Int,
        item: DocumentLineItem,
        places: Int,
        weightKg: Double
    ): List<String> {
        val net = item.totalWithVat - item.vatAmount
        val vatRateStr = when (item.vatRate) {
            -1 -> "Без НДС"
            0 -> "0%"
            else -> "${item.vatRate}%"
        }
        val vatAmtStr = if (item.vatRate < 0) "Без НДС" else RussianNumberToWords.formatMoney(item.vatAmount)
        return listOf(
            index.toString(),
            item.name,
            "-",
            item.unitName.ifBlank { "шт" },
            item.unitCode.ifBlank { "-" },
            "-",
            "-",
            if (places > 0) places.toString() else "-",
            if (weightKg > 0.0) RussianNumberToWords.formatQuantity(weightKg) else "-",
            RussianNumberToWords.formatQuantity(item.quantity),
            RussianNumberToWords.formatMoney(item.priceWithoutVat),
            RussianNumberToWords.formatMoney(net),
            vatRateStr,
            vatAmtStr,
            RussianNumberToWords.formatMoney(item.totalWithVat)
        )
    }

    private fun buildPartyRequisitesLine(
        name: String,
        inn: String,
        kpp: String,
        address: String,
        phone: String,
        bank: String,
        bik: String,
        rs: String,
        ks: String
    ): String {
        val parts = mutableListOf<String>()
        parts.add(name)
        parts.add("ИНН $inn" + if (kpp.isNotBlank()) " КПП $kpp" else "")
        if (address.isNotBlank()) parts.add(address)
        if (phone.isNotBlank()) parts.add("тел.: $phone")
        if (rs.isNotBlank() || bank.isNotBlank()) {
            val bankPart = buildString {
                if (rs.isNotBlank()) append("р/с $rs ")
                if (bank.isNotBlank()) append("в $bank ")
                if (bik.isNotBlank()) append("БИК $bik ")
                if (ks.isNotBlank()) append("к/с $ks")
            }.trim()
            parts.add(bankPart)
        }
        return parts.joinToString(", ")
    }

    private fun formatInnKpp(inn: String, kpp: String): String {
        return if (kpp.isNotBlank()) "$inn / $kpp" else inn
    }

    private fun drawRightAlignedText(canvas: Canvas, text: String, rightX: Float, y: Float, paint: Paint) {
        val w = paint.measureText(text)
        canvas.drawText(text, (rightX - w).coerceAtLeast(MARGIN_X), y, paint)
    }

    private fun drawWrappedLine(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        width: Float,
        paint: TextPaint
    ): Float {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(10))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(1f, 1.05f)
            .build()
        canvas.save()
        canvas.translate(x, y - paint.textSize)
        layout.draw(canvas)
        canvas.restore()
        return layout.height.toFloat()
    }

    private fun measureRowHeight(
        cells: List<String>,
        colWidths: FloatArray,
        paint: TextPaint,
        minHeight: Float
    ): Float {
        var maxH = minHeight
        for (i in cells.indices) {
            val cellWidth = (colWidths[i] - 4f).toInt().coerceAtLeast(8)
            val txt = cells[i]
            val layout = StaticLayout.Builder.obtain(txt, 0, txt.length, paint, cellWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()
            val h = layout.height + 6f
            if (h > maxH) maxH = h
        }
        return maxH
    }

    private fun drawTableRow(
        canvas: Canvas,
        y: Float,
        height: Float,
        cells: List<String>,
        colWidths: FloatArray,
        paint: TextPaint,
        linePaint: Paint,
        centerAll: Boolean = false,
        numericCols: Set<Int> = emptySet()
    ) {
        var x = MARGIN_X
        for (i in cells.indices) {
            val w = colWidths[i]
            canvas.drawRect(x, y, x + w, y + height, linePaint)
            val txt = cells[i]
            val align = when {
                centerAll -> Layout.Alignment.ALIGN_CENTER
                i in numericCols -> Layout.Alignment.ALIGN_OPPOSITE
                else -> Layout.Alignment.ALIGN_NORMAL
            }
            val innerWidth = (w - 4f).toInt().coerceAtLeast(6)
            val layout = StaticLayout.Builder.obtain(txt, 0, txt.length, paint, innerWidth)
                .setAlignment(align)
                .build()
            val offsetY = ((height - layout.height) / 2f).coerceAtLeast(2f)
            canvas.save()
            canvas.translate(x + 2f, y + offsetY)
            layout.draw(canvas)
            canvas.restore()
            x += w
        }
    }
}
