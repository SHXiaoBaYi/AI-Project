/** 从豆包 / DeepSeek 页面取出回答原文、思考过程、引用信源。 */

/** 按任务配置打开或关闭深度思考。豆包是「专家 / 快速」，DeepSeek 是「深度思考」开关。 */
export async function setDeepThink(page, enabled, profile = {}) {
  const host = hostname(page.url());
  if (host.endsWith('doubao.com')) return setDoubaoThink(page, enabled);
  if (host.includes('deepseek.com')) return setNamedThink(page, enabled, /^深度思考$/);
  if (profile.deepThinkSelector && enabled) {
    const loc = page.locator(profile.deepThinkSelector).filter({ visible: true }).first();
    if (await loc.count().catch(() => 0)) {
      await loc.click({ timeout: 3000 }).catch(() => {});
      return true;
    }
  }
  return setNamedThink(page, enabled, /^深度思考$/);
}

async function setDoubaoThink(page, enabled) {
  const trigger = page.locator("[data-testid='chat_input_action_model']").filter({ visible: true }).first();
  if (!(await trigger.count().catch(() => 0))) return false;
  const want = enabled ? '专家' : '快速';
  const current = ((await trigger.innerText().catch(() => '')) || '').replace(/\s+/g, '');
  if (current.includes(want)) return true;
  await trigger.click({ timeout: 4000 });
  await page.waitForTimeout(300);
  const option = page.getByText(want, { exact: true }).filter({ visible: true }).last();
  if (!(await option.count().catch(() => 0))) return false;
  await option.click({ timeout: 4000 });
  await page.waitForTimeout(300);
  const after = ((await trigger.innerText().catch(() => '')) || '').replace(/\s+/g, '');
  return after.includes(want);
}

async function setNamedThink(page, enabled, pattern) {
  const button = page.getByText(pattern).filter({ visible: true }).first();
  if (!(await button.count().catch(() => 0))) return false;
  const pressed = (await button.getAttribute('aria-pressed').catch(() => '')) || '';
  const cls = (await button.getAttribute('class').catch(() => '')) || '';
  const on = pressed === 'true' || /selected|active|checked/.test(cls);
  if (on !== enabled) {
    await button.click({ timeout: 3000 }).catch(() => {});
  }
  return true;
}

function hostname(url) {
  try {
    return new URL(url).hostname;
  } catch {
    return '';
  }
}

export async function readAnswer(page) {
  return page.evaluate(() => {
    const clean = (text) => (text || '').replace(/\u00a0/g, ' ').replace(/[ \t]+\n/g, '\n').trim();
    const stripHead = (text) => text.replace(/^搜索\s*\d+\s*个关键词[，,]\s*参考\s*\d+\s*篇资料\s*/, '');
    const content = [...document.querySelectorAll("[data-testid='message_text_content']")].at(-1);
    if (content) {
      const text = stripHead(clean(content.innerText));
      if (text.length >= 30) return text;
    }
    const receive = [...document.querySelectorAll("[data-testid='receive_message']")].at(-1);
    if (receive) {
      const text = stripHead(clean(receive.innerText));
      if (text.length >= 30) return text;
    }
    const markdown = [...document.querySelectorAll("[class*='ds-markdown']")].at(-1);
    if (markdown) {
      const text = clean(markdown.innerText);
      if (text.length >= 30) return text;
    }
    return '';
  });
}

export async function readReasoning(page) {
  const toggle = page.getByText(/已深度思考|已思考|思考过程|查看思考|思考了/).filter({ visible: true }).last();
  if (await toggle.count().catch(() => 0)) {
    await toggle.click({ timeout: 3000 }).catch(() => {});
    await page.waitForTimeout(400);
  }
  return page.evaluate(() => {
    const receive = [...document.querySelectorAll("[data-testid='receive_message']")].at(-1);
    const root = receive || document.body;
    const answer = root.querySelector("[data-testid='message_text_content']");
    const answerText = (answer?.innerText || '').trim();
    const nodes = root.querySelectorAll(
      "[class*='reasoning'],[class*='thinking'],[class*='thought'],[data-testid*='think'],[data-testid*='reasoning']",
    );
    const parts = [];
    nodes.forEach((node) => {
      if (answer && (node === answer || answer.contains(node) || node.contains(answer))) return;
      const text = (node.innerText || '').trim();
      if (text.length < 20 || text === answerText) return;
      if (parts.some((item) => item.includes(text) || text.includes(item))) return;
      parts.push(text);
    });
    return parts.join('\n\n').slice(0, 20000);
  });
}

export async function readCitations(page) {
  const opener = page
    .getByText(/参考\s*\d+\s*篇资料|已阅读\s*\d+|搜索了\s*\d+|引用\s*\d+\s*篇/)
    .filter({ visible: true })
    .last();
  if (await opener.count().catch(() => 0)) {
    await opener.click({ timeout: 4000 }).catch(() => {});
    await page.waitForTimeout(700);
  }
  const items = await page.evaluate(() => {
    const own = (host) =>
      /(^|\.)doubao\.com$|(^|\.)deepseek\.com$|(^|\.)feishu\.cn$|(^|\.)volces\.com$|(^|\.)byteimg\.com$/.test(host);
    const seen = new Set();
    const out = [];
    document.querySelectorAll("a[href^='http']").forEach((anchor) => {
      let url;
      try {
        url = new URL(anchor.href);
      } catch {
        return;
      }
      if (own(url.hostname) || seen.has(url.href)) return;
      const title = (anchor.innerText || anchor.getAttribute('title') || '')
        .trim()
        .replace(/\s+/g, ' ')
        .replace(/^\d+\.\s*/, '')
        .slice(0, 200);
      if (title.length < 4) return;
      seen.add(url.href);
      out.push({ title, site: url.hostname, url: url.href.slice(0, 1000), context: '' });
    });
    return out.slice(0, 20);
  });
  await page.keyboard.press('Escape').catch(() => {});
  return items;
}
