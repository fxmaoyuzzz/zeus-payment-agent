const messagesEl = document.querySelector("#messages");
const formEl = document.querySelector("#chatForm");
const inputEl = document.querySelector("#messageInput");
const sendButtonEl = document.querySelector("#sendButton");
const newSessionButtonEl = document.querySelector("#newSessionButton");
const sessionLabelEl = document.querySelector("#sessionLabel");

const conversationKey = "zeus-payment-agent.conversationId";
let conversationId = getOrCreateConversationId();
let activeThinkingTimer = null;

renderSession();
appendMessage("assistant", "可以开始查询订单。");

formEl.addEventListener("submit", async (event) => {
  event.preventDefault();

  const message = inputEl.value.trim();
  if (!message) {
    return;
  }

  inputEl.value = "";
  setSending(true);
  appendMessage("user", message);
  const assistantEl = appendMessage("assistant", "");
  const typingState = createTypingState(assistantEl);

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

function createTypingState(messageEl) {
  const contentEl = document.createElement("div");
  contentEl.className = "message-content";

  const progressEl = document.createElement("div");
  progressEl.className = "thinking";
  progressEl.innerHTML = `
    <span class="thinking-dot"></span>
    <span class="thinking-text">正在理解问题</span>
  `;

  messageEl.textContent = "";
  messageEl.append(progressEl, contentEl);

  const progressTexts = [
    "正在理解问题",
    "正在判断是否需要查询订单",
    "正在调用订单查询工具",
    "正在整理查询结果"
  ];
  let progressIndex = 0;
  const progressTextEl = progressEl.querySelector(".thinking-text");

  clearActiveThinkingTimer();
  activeThinkingTimer = window.setInterval(() => {
    progressIndex = Math.min(progressIndex + 1, progressTexts.length - 1);
    progressTextEl.textContent = progressTexts[progressIndex];
  }, 900);

  let queue = "";
  let writing = false;
  let finished = false;

  async function drain() {
    if (writing) {
      return;
    }

    writing = true;
    while (queue.length > 0) {
      const step = nextTextStep(queue);
      queue = queue.slice(step.length);
      contentEl.textContent += step;
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
      if (!contentEl.textContent.trim()) {
        contentEl.textContent = "没有收到有效回复。";
      }
    }
  };
}

function nextTextStep(text) {
  const punctuationIndex = text.search(/[，。！？；：\n]/);
  if (punctuationIndex >= 0 && punctuationIndex < 8) {
    return text.slice(0, punctuationIndex + 1);
  }
  return text.slice(0, Math.min(text.length, 4));
}

function getTypingDelay(step) {
  if (/[。！？\n]$/.test(step)) {
    return 180;
  }
  if (/[，；：]$/.test(step)) {
    return 110;
  }
  return 38;
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
