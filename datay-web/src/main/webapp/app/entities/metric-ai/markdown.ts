/**
 * 轻量 Markdown 渲染器（无第三方依赖）。
 *
 * 支持智能问数回答中常见的语法：标题、段落、无序/有序列表、加粗、行内代码、
 * 围栏代码块以及 GFM 表格（含 :--- 对齐）。所有文本先做 HTML 转义，避免 XSS。
 *
 * 表格渲染示例：
 * | 月份 | 销售额 |
 * |---|---:|
 * | 2024-01 | 105145.10 |
 */
export function renderMarkdown(source?: string): string {
  if (!source) {
    return "";
  }
  const lines = source.replace(/\r\n?/g, "\n").split("\n");
  const html: string[] = [];
  let i = 0;

  while (i < lines.length) {
    const line = lines[i];

    // 围栏代码块
    if (/^\s*```/.test(line)) {
      i++;
      const code: string[] = [];
      while (i < lines.length && !/^\s*```/.test(lines[i])) {
        code.push(lines[i]);
        i++;
      }
      i++; // 跳过结束围栏
      html.push(
        `<pre class="md-code"><code>${escapeHtml(code.join("\n"))}</code></pre>`,
      );
      continue;
    }

    // 表格：当前行含 |，且下一行是分隔行
    if (
      isTableRow(line) &&
      i + 1 < lines.length &&
      isTableSeparator(lines[i + 1])
    ) {
      const rendered = renderTable(lines, i);
      html.push(rendered.html);
      i = rendered.next;
      continue;
    }

    // 标题
    const heading = /^(#{1,6})\s+(.*)$/.exec(line);
    if (heading) {
      const level = heading[1].length;
      html.push(`<h${level}>${renderInline(heading[2])}</h${level}>`);
      i++;
      continue;
    }

    // 列表
    if (/^\s*([-*+]|\d+\.)\s+/.test(line)) {
      const ordered = /^\s*\d+\.\s+/.test(line);
      const items: string[] = [];
      while (i < lines.length && /^\s*([-*+]|\d+\.)\s+/.test(lines[i])) {
        items.push(renderInline(lines[i].replace(/^\s*([-*+]|\d+\.)\s+/, "")));
        i++;
      }
      const tag = ordered ? "ol" : "ul";
      html.push(
        `<${tag}>${items.map((t) => `<li>${t}</li>`).join("")}</${tag}>`,
      );
      continue;
    }

    // 空行
    if (line.trim() === "") {
      i++;
      continue;
    }

    // 段落（合并连续行，行内换行转 <br>）
    const paragraph: string[] = [];
    while (
      i < lines.length &&
      lines[i].trim() !== "" &&
      !/^\s*```/.test(lines[i]) &&
      !/^(#{1,6})\s+/.test(lines[i]) &&
      !/^\s*([-*+]|\d+\.)\s+/.test(lines[i]) &&
      !(
        isTableRow(lines[i]) &&
        i + 1 < lines.length &&
        isTableSeparator(lines[i + 1])
      )
    ) {
      paragraph.push(lines[i]);
      i++;
    }
    html.push(`<p>${paragraph.map(renderInline).join("<br>")}</p>`);
  }

  return html.join("\n");
}

function renderTable(
  lines: string[],
  start: number,
): { html: string; next: number } {
  const header = splitRow(lines[start]);
  const aligns = splitRow(lines[start + 1]).map((cell) => {
    const left = cell.startsWith(":");
    const right = cell.endsWith(":");
    if (left && right) return "center";
    if (right) return "right";
    if (left) return "left";
    return "";
  });

  let i = start + 2;
  const body: string[][] = [];
  while (
    i < lines.length &&
    isTableRow(lines[i]) &&
    !isTableSeparator(lines[i])
  ) {
    body.push(splitRow(lines[i]));
    i++;
  }

  const alignAttr = (index: number) =>
    aligns[index] ? ` style="text-align:${aligns[index]}"` : "";
  const headHtml = header
    .map((cell, index) => `<th${alignAttr(index)}>${renderInline(cell)}</th>`)
    .join("");
  const bodyHtml = body
    .map(
      (row) =>
        `<tr>${row
          .map(
            (cell, index) =>
              `<td${alignAttr(index)}>${renderInline(cell)}</td>`,
          )
          .join("")}</tr>`,
    )
    .join("");

  return {
    html: `<table class="md-table"><thead><tr>${headHtml}</tr></thead><tbody>${bodyHtml}</tbody></table>`,
    next: i,
  };
}

function isTableRow(line: string): boolean {
  return line.includes("|") && line.trim().length > 0;
}

function isTableSeparator(line: string): boolean {
  const trimmed = line.trim();
  return (
    /^\|?[\s:|-]+\|?$/.test(trimmed) &&
    /-/.test(trimmed) &&
    trimmed.includes("|")
  );
}

function splitRow(line: string): string[] {
  let trimmed = line.trim();
  if (trimmed.startsWith("|")) trimmed = trimmed.slice(1);
  if (trimmed.endsWith("|")) trimmed = trimmed.slice(0, -1);
  return trimmed.split("|").map((cell) => cell.trim());
}

function renderInline(text: string): string {
  return escapeHtml(text)
    .replace(/`([^`]+)`/g, "<code>$1</code>")
    .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
    .replace(/(^|[^*])\*([^*]+)\*/g, "$1<em>$2</em>");
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}
