import mammoth from 'mammoth'
import * as XLSX from 'xlsx'

/** 把上传文件转成评审用的纯文本。支持 docx、xlsx、xls、csv、txt、md、json。 */
export async function fileToImportText(file: File): Promise<string> {
  const name = file.name.toLowerCase()
  if (name.endsWith('.docx')) {
    const result = await mammoth.extractRawText({ arrayBuffer: await file.arrayBuffer() })
    const text = (result.value || '').replace(/\r/g, '').trim()
    if (text.length < 30) throw new Error('Word 文档提取文本过少，请确认文件非空或另存为 .txt 后重试')
    return text
  }
  if (name.endsWith('.doc')) throw new Error('暂不支持旧版 .doc，请用 Word 另存为 .docx 后再上传')
  if (name.endsWith('.pdf')) throw new Error('暂不支持 PDF，请导出为 .docx 或 .txt 后上传')
  if (name.endsWith('.xlsx') || name.endsWith('.xls')) {
    const wb = XLSX.read(await file.arrayBuffer(), { type: 'array' })
    if (!wb.SheetNames.length) throw new Error('Excel 中没有工作表')
    const parts: string[] = []
    for (const sheetName of wb.SheetNames) {
      if (/^WpsReserved/i.test(sheetName)) continue
      const csv = XLSX.utils.sheet_to_csv(wb.Sheets[sheetName])
      if (csv.trim()) parts.push(`【工作表: ${sheetName}】\n${csv}`)
    }
    const text = parts.join('\n\n').trim()
    if (text.length < 10) throw new Error('Excel 内容为空，请检查文件')
    return text
  }
  const text = (await file.text()).replace(/\r/g, '').trim()
  if (text.length < 10) throw new Error('文件内容过短或为空')
  if (text.includes('PK\u0003\u0004') || text.startsWith('PK')) {
    throw new Error('检测到压缩或二进制内容，请直接上传 .docx / .xlsx，或导出为 CSV、TXT')
  }
  return text
}
