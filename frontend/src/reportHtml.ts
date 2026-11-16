/** 报告原文是 Markdown。先转义再生成标签，页面只插入这里的结果。 */

const REPORT_CSS = `
  body { margin: 0; background: #f3f5f8; color: #1d2430; font-family: "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif; }
  .report-sheet { max-width: 980px; margin: 24px auto; background: #fff; padding: 28px 32px 36px; border: 1px solid #e7ebf1; border-radius: 8px; }
  h1 { font-size: 22px; line-height: 1.35; margin: 0 0 16px; padding-bottom: 12px; border-bottom: 1px solid #e7ebf1; }
  h2 { font-size: 16px; margin: 22px 0 10px; padding-left: 10px; border-left: 3px solid #1d6fe8; }
  h3 { font-size: 14px; margin: 16px 0 8px; }
  p { margin: 8px 0; line-height: 1.7; }
  .meta { display: grid; grid-template-columns: 1fr 1fr; gap: 8px 16px; margin: 0 0 8px; }
  .meta div { display: flex; gap: 8px; background: #f7f9fc; border-radius: 6px; padding: 8px 10px; }
  .meta dt { color: #8b95a5; flex: none; }
  .meta dd { margin: 0; }
  ul, ol { margin: 8px 0; padding-left: 1.3em; }
  li { margin: 4px 0; line-height: 1.6; }
  .table-wrap { overflow-x: auto; margin: 8px 0 4px; }
  table { width: 100%; border-collapse: collapse; font-size: 13px; }
  th, td { border: 1px solid #e7ebf1; padding: 8px 10px; text-align: left; vertical-align: top; }
  th { background: #f6f8fb; font-weight: 600; white-space: nowrap; }
  blockquote { margin: 12px 0; padding: 10px 12px; background: #fff7ed; border-left: 3px solid #d97706; color: #9a3412; }
  blockquote p { margin: 0; }
  hr { border: 0; border-top: 1px solid #e7ebf1; margin: 18px 0; }
  code { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; background: #f3f5f8; padding: 1px 4px; border-radius: 4px; }
  .foot { color: #8b95a5; font-size: 12px; }
  .tag { display: inline-block; min-width: 1.6em; text-align: center; border-radius: 4px; padding: 0 6px; font-size: 12px; line-height: 20px; }
  .tag-red { background: #fee2e2; color: #b91c1c; }
  .tag-orange { background: #ffedd5; color: #c2410c; }
  .tag-yellow { background: #fef3c7; color: #b45309; }
  .tag-yes { background: #fee2e2; color: #b91c1c; }
  .tag-no { background: #f3f5f8; color: #64748b; }
  @media print { body { background: #fff; } .report-sheet { margin: 0; border: 0; max-width: none; } }
`

export function renderReport(markdown: string): string {
  const lines = (markdown || '').replace(/\r\n/g, '\n').split('\n')
  const html: string[] = []
  let i = 0
  while (i < lines.length) {
    const line = lines[i]
    if (!line.trim()) { i++; continue }
    if (line.startsWith('|') && isSep(lines[i + 1] || '')) {
      const rows = [splitRow(line)]
      i += 2
      while (i < lines.length && lines[i].startsWith('|')) { rows.push(splitRow(lines[i])); i++ }
      html.push(table(rows))
      continue
    }
    const heading = /^(#{1,3}) (.+)$/.exec(line)
    if (heading) { html.push(`<h${heading[1].length}>${inline(heading[2])}</h${heading[1].length}>`); i++; continue }
    if (line.trim() === '---') { html.push('<hr />'); i++; continue }
    if (line.startsWith('> ')) {
      const quote: string[] = []
      while (i < lines.length && lines[i].startsWith('> ')) { quote.push(lines[i].slice(2)); i++ }
      html.push(`<blockquote><p>${inline(quote.join(' '))}</p></blockquote>`)
      continue
    }
    if (line.startsWith('- ')) {
      const items: string[] = []
      while (i < lines.length && lines[i].startsWith('- ')) { items.push(lines[i].slice(2)); i++ }
      html.push(listOrMeta(items))
      continue
    }
    if (/^\d+\. /.test(line)) {
      const items: string[] = []
      while (i < lines.length && /^\d+\. /.test(lines[i])) { items.push(lines[i].replace(/^\d+\. /, '')); i++ }
      html.push('<ol>' + items.map((item) => `<li>${inline(item)}</li>`).join('') + '</ol>')
      continue
    }
    const para: string[] = []
    while (i < lines.length && lines[i].trim() && !blockStart(lines, i)) { para.push(lines[i]); i++ }
    const text = para.join('\n')
    const foot = /^\*([^*]+)\*$/.exec(text.trim())
    html.push(foot ? `<p class="foot">${inline(foot[1])}</p>` : `<p>${inline(text).replace(/\n/g, '<br />')}</p>`)
  }
  return html.join('\n')
}

export function reportDocument(title: string, markdown: string): string {
  const safeTitle = inline(title || '报告')
  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>${safeTitle}</title>
<style>${REPORT_CSS}</style>
</head>
<body>
<article class="report-sheet">
${renderReport(markdown)}
</article>
</body>
</html>
`
}

function blockStart(lines: string[], i: number): boolean {
  const line = lines[i]
  return line.startsWith('|') && isSep(lines[i + 1] || '')
    || /^(#{1,3}) /.test(line)
    || line.trim() === '---'
    || line.startsWith('> ')
    || line.startsWith('- ')
    || /^\d+\. /.test(line)
}

function isSep(line: string): boolean {
  return /^\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?$/.test(line.trim())
}

function splitRow(line: string): string[] {
  const parts = line.split('|')
  if (line.trim().startsWith('|')) parts.shift()
  if (line.trim().endsWith('|')) parts.pop()
  return parts.map((cell) => cell.trim())
}

function table(rows: string[][]): string {
  const head = rows[0].map((cell) => `<th>${inline(cell)}</th>`).join('')
  const body = rows.slice(1).map((row) => '<tr>' + row.map((cell) => `<td>${chip(cell)}</td>`).join('') + '</tr>').join('')
  return `<div class="table-wrap"><table><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table></div>`
}

function listOrMeta(items: string[]): string {
  const pairs: { label: string; value: string }[] = []
  for (const item of items) {
    const matched = /^(.{1,8})[：:](.*)$/.exec(item.trim())
    if (!matched) return '<ul>' + items.map((it) => `<li>${inline(it)}</li>`).join('') + '</ul>'
    pairs.push({ label: matched[1], value: matched[2].trim() })
  }
  return '<dl class="meta">' + pairs.map((p) => `<div><dt>${inline(p.label)}</dt><dd>${inline(p.value)}</dd></div>`).join('') + '</dl>'
}

function chip(raw: string): string {
  const text = raw.trim()
  if (text === '红') return '<span class="tag tag-red">红</span>'
  if (text === '橙') return '<span class="tag tag-orange">橙</span>'
  if (text === '黄') return '<span class="tag tag-yellow">黄</span>'
  if (text === '是') return '<span class="tag tag-yes">是</span>'
  if (text === '否') return '<span class="tag tag-no">否</span>'
  return inline(text)
}

function inline(s: string): string {
  return escapeHtml(s)
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/(^|[^*])\*([^*\n]+)\*/g, '$1<em>$2</em>')
}

function escapeHtml(s: string): string {
  return s.replace(/[&<>"]/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[ch] || ch))
}
