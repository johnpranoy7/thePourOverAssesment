type Block =
  | { type: "text"; text: string }
  | { type: "table"; headers: string[]; rows: string[][] };

function splitCells(line: string): string[] {
  return line
    .trim()
    .replace(/^\|/, "")
    .replace(/\|$/, "")
    .split("|")
    .map((c) => c.trim());
}

function isSeparatorRow(line: string): boolean {
  const cells = splitCells(line);
  return cells.length > 0 && cells.every((c) => /^:?-{3,}:?$/.test(c));
}

function isTableRow(line: string): boolean {
  const t = line.trim();
  return t.startsWith("|") && t.includes("|", 1);
}

/** Render `**bold**` spans; leave unmatched markers as plain text. */
function renderInlineMarkdown(text: string) {
  const parts = text.split(/(\*\*[^*]+\*\*)/g);
  return parts.map((part, i) => {
    if (part.startsWith("**") && part.endsWith("**") && part.length > 4) {
      return <strong key={i}>{part.slice(2, -2)}</strong>;
    }
    return part;
  });
}

/** Split assistant markdown into text + pipe tables. */
export function parseMessageBlocks(content: string): Block[] {
  const lines = content.replace(/\r\n/g, "\n").split("\n");
  const blocks: Block[] = [];
  let textBuf: string[] = [];

  function flushText() {
    const text = textBuf.join("\n").trim();
    if (text) blocks.push({ type: "text", text });
    textBuf = [];
  }

  let i = 0;
  while (i < lines.length) {
    if (
      isTableRow(lines[i]) &&
      i + 1 < lines.length &&
      isSeparatorRow(lines[i + 1])
    ) {
      flushText();
      const headers = splitCells(lines[i]);
      i += 2;
      const rows: string[][] = [];
      while (i < lines.length && isTableRow(lines[i]) && !isSeparatorRow(lines[i])) {
        const cells = splitCells(lines[i]);
        while (cells.length < headers.length) cells.push("");
        rows.push(cells.slice(0, headers.length));
        i += 1;
      }
      blocks.push({ type: "table", headers, rows });
      continue;
    }
    textBuf.push(lines[i]);
    i += 1;
  }
  flushText();
  return blocks;
}

export default function ChatMessage({
  role,
  content,
}: {
  role: "user" | "assistant";
  content: string;
}) {
  if (role === "user") {
    return <div className="bubble user">{content}</div>;
  }

  const blocks = parseMessageBlocks(content);

  return (
    <div className="bubble assistant">
      {blocks.map((block, idx) => {
        if (block.type === "text") {
          return (
            <p key={idx} className="chat-text">
              {renderInlineMarkdown(block.text)}
            </p>
          );
        }
        return (
          <div key={idx} className="chat-table-wrap">
            <table className="chat-table">
              <thead>
                <tr>
                  {block.headers.map((h, hIdx) => (
                    <th key={hIdx}>{renderInlineMarkdown(h)}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {block.rows.map((row, rIdx) => (
                  <tr key={rIdx}>
                    {row.map((cell, cIdx) => (
                      <td key={cIdx}>{renderInlineMarkdown(cell)}</td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        );
      })}
    </div>
  );
}
