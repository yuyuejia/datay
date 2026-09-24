import { describe, expect, it } from "vitest";

import { renderMarkdown } from "./markdown";

describe("renderMarkdown", () => {
  it("should render a GFM table with alignment", () => {
    const html = renderMarkdown(
      ["| 月份 | 销售额 |", "|---|---:|", "| 2024-01 | 105145.10 |"].join("\n"),
    );
    expect(html).toContain('<table class="md-table">');
    expect(html).toContain("<th>月份</th>");
    expect(html).toContain('<th style="text-align:right">销售额</th>');
    expect(html).toContain("<td>2024-01</td>");
    expect(html).toContain('<td style="text-align:right">105145.10</td>');
  });

  it("should render bold, inline code and headings", () => {
    const html = renderMarkdown("## 查询结论\n\n**销售额** 为 `sales_amount`");
    expect(html).toContain("<h2>查询结论</h2>");
    expect(html).toContain("<strong>销售额</strong>");
    expect(html).toContain("<code>sales_amount</code>");
  });

  it("should escape raw HTML to avoid XSS", () => {
    const html = renderMarkdown("<script>alert(1)</script>");
    expect(html).not.toContain("<script>");
    expect(html).toContain("&lt;script&gt;");
  });
});
