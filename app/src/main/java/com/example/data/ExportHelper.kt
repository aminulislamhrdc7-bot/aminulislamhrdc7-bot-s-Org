package com.example.data

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.example.model.AppLanguage
import com.example.model.CurrencyCode
import com.example.model.ExpenseCategory
import com.example.model.ExpenseEntry
import com.example.model.IncomeEntry
import com.example.model.IncomeSource
import com.example.ui.strings.AppStrings
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportHelper {

    fun writePdfReport(
        context: Context,
        uri: Uri,
        language: AppLanguage,
        periodTitle: String,
        incomes: List<IncomeEntry>,
        expenses: List<ExpenseEntry>,
        sarToBdtRate: Double
    ): Result<Unit> = runCatching {
        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f

        val titlePaint = Paint().apply {
            color = Color.rgb(13, 92, 70)
            textSize = 20f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val subtitlePaint = Paint().apply {
            color = Color.rgb(73, 90, 83)
            textSize = 12f
            isAntiAlias = true
        }
        val sectionPaint = Paint().apply {
            color = Color.rgb(17, 28, 24)
            textSize = 14f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 11f
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 1f
        }

        var pageNumber = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = 50f

        fun newPageIfNeeded(requiredHeight: Float = 24f) {
            if (y + requiredHeight > pageHeight - 40f) {
                pdfDocument.finishPage(page)
                pageNumber++
                page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = 50f
            }
        }

        val strings = AppStrings.forLanguage(language)
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        canvas.drawText("${strings.appName} - ${strings.reportsTitle}", margin, y, titlePaint)
        y += 22f
        canvas.drawText("$periodTitle  |  ${dateFormat.format(Date())}  |  1 SAR = $sarToBdtRate BDT", margin, y, subtitlePaint)
        y += 14f
        canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
        y += 24f

        val incBdt = incomes.filter { it.currency == CurrencyCode.BDT.code }.sumOf { it.amount }
        val incSar = incomes.filter { it.currency == CurrencyCode.SAR.code }.sumOf { it.amount }
        val expBdt = expenses.filter { it.currency == CurrencyCode.BDT.code }.sumOf { it.amount }
        val expSar = expenses.filter { it.currency == CurrencyCode.SAR.code }.sumOf { it.amount }

        canvas.drawText(strings.summarySection, margin, y, sectionPaint)
        y += 20f
        canvas.drawText("${strings.totalIncome} (BDT): ৳ ${"%.2f".format(incBdt)}   |   (SAR): ﷼ ${"%.2f".format(incSar)}", margin, y, bodyPaint)
        y += 18f
        canvas.drawText("${strings.totalExpense} (BDT): ৳ ${"%.2f".format(expBdt)}   |   (SAR): ﷼ ${"%.2f".format(expSar)}", margin, y, bodyPaint)
        y += 18f
        canvas.drawText("${strings.netBalance} (BDT): ৳ ${"%.2f".format(incBdt - expBdt)}   |   (SAR): ﷼ ${"%.2f".format(incSar - expSar)}", margin, y, bodyPaint)
        y += 26f

        // Category Breakdown
        canvas.drawText(strings.categoryBreakdown, margin, y, sectionPaint)
        y += 18f
        val byCategory = expenses.groupBy { ExpenseCategory.fromCode(it.category) }
        if (byCategory.isEmpty()) {
            canvas.drawText(strings.noExpensesYet, margin, y, bodyPaint)
            y += 18f
        } else {
            byCategory.forEach { (cat, list) ->
                newPageIfNeeded()
                val bdtSum = list.filter { it.currency == CurrencyCode.BDT.code }.sumOf { it.amount }
                val sarSum = list.filter { it.currency == CurrencyCode.SAR.code }.sumOf { it.amount }
                val label = strings.expenseCategoryLabel(cat)
                canvas.drawText("• $label: ৳ ${"%.2f".format(bdtSum)}  |  ﷼ ${"%.2f".format(sarSum)}", margin + 8f, y, bodyPaint)
                y += 16f
            }
        }
        y += 16f

        // Income List
        newPageIfNeeded(40f)
        canvas.drawText(strings.incomeHistory, margin, y, sectionPaint)
        y += 18f
        if (incomes.isEmpty()) {
            canvas.drawText(strings.noIncomeYet, margin, y, bodyPaint)
            y += 18f
        } else {
            incomes.forEach { inc ->
                newPageIfNeeded()
                val srcLabel = strings.incomeSourceLabel(IncomeSource.fromCode(inc.source))
                val dateStr = dateFormat.format(Date(inc.dateMillis))
                val note = if (inc.description.isNotBlank()) " (${inc.description.take(40)})" else ""
                canvas.drawText("$dateStr  |  $srcLabel  |  ${inc.currency} ${"%.2f".format(inc.amount)}$note", margin + 4f, y, bodyPaint)
                y += 16f
            }
        }
        y += 16f

        // Expense List
        newPageIfNeeded(40f)
        canvas.drawText(strings.expenseHistory, margin, y, sectionPaint)
        y += 18f
        if (expenses.isEmpty()) {
            canvas.drawText(strings.noExpensesYet, margin, y, bodyPaint)
            y += 18f
        } else {
            expenses.forEach { exp ->
                newPageIfNeeded()
                val catLabel = strings.expenseCategoryLabel(ExpenseCategory.fromCode(exp.category))
                val dateStr = dateFormat.format(Date(exp.dateMillis))
                val note = if (exp.description.isNotBlank()) " (${exp.description.take(40)})" else ""
                canvas.drawText("$dateStr  |  $catLabel  |  ${exp.currency} ${"%.2f".format(exp.amount)}$note", margin + 4f, y, bodyPaint)
                y += 16f
            }
        }

        pdfDocument.finishPage(page)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            pdfDocument.writeTo(out)
        } ?: error("Unable to open destination file")
        pdfDocument.close()
    }

    fun writeCsvExcelReport(
        context: Context,
        uri: Uri,
        language: AppLanguage,
        incomes: List<IncomeEntry>,
        expenses: List<ExpenseEntry>
    ): Result<Unit> = runCatching {
        val strings = AppStrings.forLanguage(language)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        context.contentResolver.openOutputStream(uri)?.use { out ->
            // Write UTF-8 BOM so Microsoft Excel renders Bengali characters properly
            out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            OutputStreamWriter(out, StandardCharsets.UTF_8).use { writer ->
                writer.appendLine("Type,Date,CategoryOrSource,Currency,Amount,Description")
                incomes.forEach { inc ->
                    val source = strings.incomeSourceLabel(IncomeSource.fromCode(inc.source)).escapeCsv()
                    val desc = inc.description.escapeCsv()
                    writer.appendLine("${strings.incomeTab.escapeCsv()},${dateFormat.format(Date(inc.dateMillis))},$source,${inc.currency},${inc.amount},$desc")
                }
                expenses.forEach { exp ->
                    val category = strings.expenseCategoryLabel(ExpenseCategory.fromCode(exp.category)).escapeCsv()
                    val desc = exp.description.escapeCsv()
                    writer.appendLine("${strings.expenseTab.escapeCsv()},${dateFormat.format(Date(exp.dateMillis))},$category,${exp.currency},${exp.amount},$desc")
                }
            }
        } ?: error("Unable to open destination file")
    }

    fun writeTextFile(context: Context, uri: Uri, content: String): Result<Unit> = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            OutputStreamWriter(out, StandardCharsets.UTF_8).use { it.write(content) }
        } ?: error("Unable to write backup file")
    }

    fun readTextFile(context: Context, uri: Uri): Result<String> = runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader(StandardCharsets.UTF_8)?.use {
            it.readText()
        } ?: error("Unable to read backup file")
    }

    private fun String.escapeCsv(): String {
        val cleaned = replace("\"", "\"\"").replace("\n", " ").replace("\r", " ")
        return "\"$cleaned\""
    }
}
