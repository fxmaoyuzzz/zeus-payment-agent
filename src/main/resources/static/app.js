const messagesEl = document.querySelector("#messages");
const formEl = document.querySelector("#chatForm");
const inputEl = document.querySelector("#messageInput");
const sendButtonEl = document.querySelector("#sendButton");
const newSessionButtonEl = document.querySelector("#newSessionButton");
const sessionLabelEl = document.querySelector("#sessionLabel");
const loadReportButtonEl = document.querySelector("#loadReportButton");
const reportDateInputEl = document.querySelector("#reportDateInput");
const reportPanelEl = document.querySelector("#reportPanel");
const reportSummaryEl = document.querySelector("#reportSummary");
const statusChartEl = document.querySelector("#statusChart");
const channelChartEl = document.querySelector("#channelChart");
const failureChartEl = document.querySelector("#failureChart");

const conversationKey = "zeus-payment-agent.conversationId";
let conversationId = getOrCreateConversationId();
let activeThinkingTimer = null;

renderSession();
appendMessage("assistant", "你好，我是 Zeus Payment Agent，可以帮你查询订单、分析支付失败原因、检索支付知识库或生成支付日报。");
resizeComposerInput();

formEl.addEventListener("submit", async (event) => {
  event.preventDefault();

  const message = inputEl.value.trim();
  if (!message) {
    return;
  }

  inputEl.value = "";
  resizeComposerInput();
  setSending(true);
  appendMessage("user", message);
  const assistantEl = appendMessage("assistant", "");
  const typingState = createTypingState(assistantEl, message);

  try {
    await streamChat(message, typingState);
  }
  catch (error) {
    typingState.finish();
    assistantEl.remove();
    appendMessage("error", error.message || "请求失败");
  }
  finally {
    setSending(false);
    inputEl.focus();
  }
});

newSessionButtonEl.addEventListener("click", () => {
  conversationId = createConversationId();
  localStorage.setItem(conversationKey, conversationId);
  messagesEl.innerHTML = "";
  renderSession();
  appendMessage("assistant", "已开始新会话。");
  inputEl.focus();
});

loadReportButtonEl.addEventListener("click", async () => {
  await loadDailyReportChart();
});

inputEl.addEventListener("input", resizeComposerInput);

inputEl.addEventListener("keydown", (event) => {
  if (event.key === "Enter" && !event.shiftKey) {
    event.preventDefault();
    formEl.requestSubmit();
  }
});

async function streamChat(message, typingState) {
  const response = await fetch("/api/chat/stream", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Accept": "text/event-stream"
    },
    body: JSON.stringify({ conversationId, message })
  });

  if (!response.ok || !response.body) {
    throw new Error(`请求失败：HTTP ${response.status}`);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";

  while (true) {
    const { value, done } = await reader.read();
    if (done) {
      break;
    }

    buffer += decoder.decode(value, { stream: true });
    const events = buffer.split("\n\n");
    buffer = events.pop() || "";

    for (const eventText of events) {
      await handleSseEvent(eventText, typingState);
    }
  }

  if (buffer.trim()) {
    await handleSseEvent(buffer, typingState);
  }

  await typingState.flush();
  typingState.finish();
}

async function handleSseEvent(eventText, typingState) {
  const lines = eventText.split("\n");
  let eventName = "message";
  const dataLines = [];

  for (const line of lines) {
    if (line.startsWith("event:")) {
      eventName = line.slice(6).trim();
    }
    else if (line.startsWith("data:")) {
      dataLines.push(line.slice(5).trimStart());
    }
  }

  const data = dataLines.join("\n");
  if (eventName === "message") {
    typingState.hideProgress();
    typingState.enqueue(data);
  }
  else if (eventName === "error") {
    throw new Error(data || "流式请求失败");
  }
}

function createTypingState(messageEl, userMessage) {
  const contentEl = document.createElement("div");
  contentEl.className = "message-content";
  const waitingEl = document.createElement("span");
  waitingEl.className = "waiting-dots";
  waitingEl.setAttribute("aria-label", "处理中");
  waitingEl.innerHTML = "<span></span><span></span><span></span>";

  const progressEl = document.createElement("div");
  progressEl.className = "thinking";
  progressEl.innerHTML = `
    <span class="thinking-dot"></span>
    <span class="thinking-text">正在理解问题</span>
  `;

  messageEl.textContent = "";
  messageEl.append(progressEl, contentEl, waitingEl);

  const progressTexts = getProgressTexts(userMessage);
  let progressIndex = 0;
  const progressTextEl = progressEl.querySelector(".thinking-text");

  clearActiveThinkingTimer();
  activeThinkingTimer = window.setInterval(() => {
    progressIndex = Math.min(progressIndex + 1, progressTexts.length - 1);
    progressTextEl.textContent = progressTexts[progressIndex];
  }, 900);

  let queue = "";
  let rawContent = "";
  let writing = false;
  let finished = false;
  let lastRenderAt = 0;

  function renderNow() {
    contentEl.innerHTML = renderAssistantContent(rawContent);
    lastRenderAt = Date.now();
  }

  async function drain() {
    if (writing) {
      return;
    }

    writing = true;
    while (queue.length > 0) {
      const step = nextTextStep(queue);
      queue = queue.slice(step.length);
      rawContent += step;
      if (Date.now() - lastRenderAt > 120 || queue.length === 0) {
        renderNow();
      }
      scrollToBottom();
      await delay(getTypingDelay(step));
    }
    writing = false;
  }

  return {
    enqueue(text) {
      if (!text || finished) {
        return;
      }
      queue += text;
      void drain();
    },
    async flush() {
      while (queue.length > 0 || writing) {
        await delay(40);
      }
    },
    hideProgress() {
      clearActiveThinkingTimer();
      progressEl.remove();
    },
    finish() {
      finished = true;
      clearActiveThinkingTimer();
      progressEl.remove();
      waitingEl.remove();
      if (!rawContent.trim()) {
        contentEl.textContent = "没有收到有效回复。";
        return;
      }

      renderNow();
    }
  };
}

function getProgressTexts(message) {
  const normalizedMessage = message.toLowerCase();

  if (/(日报|日.?报|统计|概览|报表|daily|report)/i.test(normalizedMessage)) {
    return [
      "正在理解统计范围",
      "正在聚合支付数据",
      "正在计算关键指标",
      "正在生成日报摘要"
    ];
  }

  if (/(失败|原因|为什么|异常|报错|错误码|fail|error|timeout|拒绝|风控)/i.test(normalizedMessage)) {
    return [
      "正在理解失败场景",
      "正在收集支付上下文",
      "正在分析异常证据",
      "正在整理处理建议"
    ];
  }

  if (/(知识库|文档|规则|SOP|流程|渠道规则|怎么处理|规范|knowledge|rag)/i.test(normalizedMessage)) {
    return [
      "正在理解检索意图",
      "正在匹配知识库文档",
      "正在筛选相关片段",
      "正在组织参考答案"
    ];
  }

  if (/(流水|交易|transaction|支付记录|支付单|支付流水)/i.test(normalizedMessage)) {
    return [
      "正在理解支付流水条件",
      "正在判断需要的查询工具",
      "正在读取支付流水数据",
      "正在整理流水结果"
    ];
  }

  if (/(订单|order|单号|用户id|状态)/i.test(normalizedMessage)) {
    return [
      "正在理解订单条件",
      "正在判断需要的查询工具",
      "正在读取订单数据",
      "正在整理查询结果"
    ];
  }

  return [
    "正在理解问题",
    "正在判断需要的能力",
    "正在调用相关工具",
    "正在整理回答"
  ];
}

function renderAssistantContent(text) {
  try {
    const normalizedText = normalizeMarkdownTables(text);
    const lines = normalizedText.split("\n");
    const htmlParts = [];
    let paragraphLines = [];

    function flushParagraph() {
      if (paragraphLines.length === 0) {
        return;
      }

    htmlParts.push(`<p>${renderInlineMarkdown(paragraphLines.join("\n"))}</p>`);
      paragraphLines = [];
    }

    for (let index = 0; index < lines.length; index++) {
      const line = lines[index];
      const trimmed = line.trim();

      if (!trimmed) {
        flushParagraph();
        continue;
      }

      const heading = trimmed.match(/^(#{1,4})\s*(.+)$/);
      if (heading) {
        flushParagraph();
        const level = Math.min(heading[1].length + 1, 4);
      htmlParts.push(`<h${level}>${renderInlineMarkdown(heading[2])}</h${level}>`);
        continue;
      }

      if (isTableBlockStart(lines, index)) {
        flushParagraph();
        const tableLines = [];
        while (index < lines.length && isTableLine(lines[index])) {
          tableLines.push(lines[index]);
          index++;
        }
        index--;
        htmlParts.push(renderMarkdownTable(tableLines));
        continue;
      }

      paragraphLines.push(line);
    }

    flushParagraph();
    return htmlParts.join("");
  }
  catch (error) {
    return `<p>${renderInlineMarkdown(text)}</p>`;
  }
}

function normalizeMarkdownTables(text) {
  return text
    .replace(/([^\n])(\s+\d+\.\s*\*\*)/g, "$1\n$2")
    .replace(/([^\n])(\s+\d+\.\s)/g, "$1\n$2")
    .replace(/^(#{1,4})([^|\n]+)(\|.+)$/gm, "$1 $2\n$3")
    .replace(/(\|[^\n]*\|)(#{1,4})/g, "$1\n$2");
}

function isTableBlockStart(lines, index) {
  if (!isTableLine(lines[index] || "")) {
    return false;
  }

  if (isTableDivider(lines[index + 1] || "")) {
    return true;
  }

  return isTableLine(lines[index + 1] || "");
}

function isTableLine(line) {
  const trimmed = line.trim();
  return trimmed.startsWith("|") && trimmed.endsWith("|") && splitTableRow(trimmed).length >= 2;
}

function isTableDivider(line) {
  return /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$/.test(line);
}

function renderMarkdownTable(tableLines) {
  const rows = tableLines
    .filter((line) => line.trim())
    .filter((line) => !isTableDivider(line))
    .map(splitTableRow);

  if (rows.length < 2) {
    return `<p>${escapeHtml(tableLines.join("\n"))}</p>`;
  }

  const headerCells = rows[0] || [];
  const bodyRows = rows.slice(1);

  const headerHtml = headerCells
    .map((cell) => `<th>${renderInlineMarkdown(cell)}</th>`)
    .join("");
  const bodyHtml = bodyRows
    .map((row) => `<tr>${row.map((cell) => `<td>${renderInlineMarkdown(cell)}</td>`).join("")}</tr>`)
    .join("");

  return `
    <div class="table-wrap">
      <table>
        <thead><tr>${headerHtml}</tr></thead>
        <tbody>${bodyHtml}</tbody>
      </table>
    </div>
  `;
}

function splitTableRow(line) {
  return line
    .trim()
    .replace(/^\|/, "")
    .replace(/\|$/, "")
    .split("|")
    .map((cell) => cell.trim());
}

function escapeHtml(value) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function renderInlineMarkdown(value) {
  return escapeHtml(value)
    .replace(/`([^`]+)`/g, "<code>$1</code>")
    .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
}

function nextTextStep(text) {
  const punctuationIndex = text.search(/[。！？\n]/);
  if (punctuationIndex >= 0 && punctuationIndex < 18) {
    return text.slice(0, punctuationIndex + 1);
  }
  return text.slice(0, Math.min(text.length, 10));
}

function getTypingDelay(step) {
  if (/[。！？\n]$/.test(step)) {
    return 120;
  }
  if (/[，；：]$/.test(step)) {
    return 60;
  }
  return 26;
}

function delay(ms) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

function clearActiveThinkingTimer() {
  if (activeThinkingTimer) {
    window.clearInterval(activeThinkingTimer);
    activeThinkingTimer = null;
  }
}

function appendMessage(role, content) {
  const rowEl = document.createElement("div");
  rowEl.className = `message-row ${role}`;

  const avatarEl = document.createElement("img");
  avatarEl.className = "avatar";
  avatarEl.alt = role === "user" ? "用户头像" : "AI 头像";
  avatarEl.src = role === "user" ? "/user-chat.svg" : "/ai-chat.svg";

  const messageEl = document.createElement("div");
  messageEl.className = `message ${role}`;
  messageEl.textContent = content;

  if (role === "user") {
    rowEl.append(messageEl, avatarEl);
  }
  else {
    rowEl.append(avatarEl, messageEl);
  }

  messagesEl.appendChild(rowEl);
  scrollToBottom();
  return messageEl;
}

function setSending(isSending) {
  inputEl.disabled = isSending;
  sendButtonEl.disabled = isSending;
  newSessionButtonEl.disabled = isSending;
  loadReportButtonEl.disabled = isSending;
  if (!isSending) {
    resizeComposerInput();
  }
}

function resizeComposerInput() {
  inputEl.style.height = "auto";
  inputEl.style.height = `${Math.min(inputEl.scrollHeight, 180)}px`;
}

async function loadDailyReportChart() {
  const reportDate = reportDateInputEl.value;
  if (!reportDate) {
    appendMessage("error", "请选择日报日期。");
    return;
  }

  loadReportButtonEl.disabled = true;
  try {
    const response = await fetch(`/api/admin/reports/daily-payment/chart?reportDate=${encodeURIComponent(reportDate)}`);
    if (!response.ok) {
      throw new Error(`日报图表加载失败：HTTP ${response.status}`);
    }

    const data = await response.json();
    reportPanelEl.hidden = false;
    reportSummaryEl.textContent = `${data.reportDate}：总单 ${data.totalOrders}，成功 ${data.successOrders}，失败 ${data.failedOrders}，处理中 ${data.pendingOrders}`;
    drawStatusChart(statusChartEl, data);
    drawBarChart(
      channelChartEl,
      (data.channelStats || []).slice(0, 6).map((item) => ({
        label: item.channelCode,
        value: Number(item.totalCount || 0),
        color: "#2563eb"
      })),
      "单"
    );
    drawBarChart(
      failureChartEl,
      (data.failureStats || []).slice(0, 6).map((item) => ({
        label: item.failureCode || "UNKNOWN",
        value: Number(item.failureCount || 0),
        color: "#dc2626"
      })),
      "次"
    );
  }
  catch (error) {
    appendMessage("error", error.message || "日报图表加载失败");
  }
  finally {
    loadReportButtonEl.disabled = false;
  }
}

function drawStatusChart(canvas, data) {
  const rows = [
    { label: "成功", value: Number(data.successOrders || 0), color: "#16a34a" },
    { label: "失败", value: Number(data.failedOrders || 0), color: "#dc2626" },
    { label: "处理中", value: Number(data.pendingOrders || 0), color: "#f59e0b" }
  ];
  drawBarChart(canvas, rows, "单");
}

function drawBarChart(canvas, rows, unit) {
  const context = canvas.getContext("2d");
  const width = canvas.width;
  const height = canvas.height;
  const padding = 28;
  const labelWidth = 92;
  const maxValue = Math.max(...rows.map((row) => row.value), 1);

  context.clearRect(0, 0, width, height);
  context.fillStyle = "#ffffff";
  context.fillRect(0, 0, width, height);

  if (rows.length === 0) {
    context.fillStyle = "#64748b";
    context.font = "13px sans-serif";
    context.fillText("暂无数据", padding, height / 2);
    return;
  }

  const rowHeight = Math.min(24, (height - padding * 2) / rows.length);
  rows.forEach((row, index) => {
    const y = padding + index * rowHeight;
    const barWidth = Math.max(2, (width - labelWidth - padding * 2) * row.value / maxValue);
    context.fillStyle = "#475569";
    context.font = "12px sans-serif";
    context.fillText(shortLabel(row.label), padding, y + 13);
    context.fillStyle = row.color;
    context.fillRect(labelWidth, y, barWidth, 14);
    context.fillStyle = "#0f172a";
    context.fillText(`${row.value}${unit}`, labelWidth + barWidth + 6, y + 12);
  });
}

function shortLabel(label) {
  if (!label) {
    return "UNKNOWN";
  }
  return label.length > 12 ? `${label.slice(0, 11)}...` : label;
}

function getOrCreateConversationId() {
  const saved = localStorage.getItem(conversationKey);
  if (saved) {
    return saved;
  }

  const created = createConversationId();
  localStorage.setItem(conversationKey, created);
  return created;
}

function createConversationId() {
  return `web-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function renderSession() {
  sessionLabelEl.textContent = `会话：${conversationId}`;
}

function scrollToBottom() {
  messagesEl.scrollTop = messagesEl.scrollHeight;
}
