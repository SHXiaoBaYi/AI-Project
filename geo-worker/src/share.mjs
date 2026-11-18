/** 只返回别人无需登录就能打开的分享链接。私有对话地址一律丢掉。 */

export async function shareLink(page, profile = {}) {
  await page.context().grantPermissions(['clipboard-read', 'clipboard-write']).catch(() => {});
  await installShareHook(page);
  const host = hostname(page.url());
  let raw = '';
  if (host.endsWith('doubao.com')) raw = await shareDoubao(page);
  else if (host.includes('deepseek.com')) raw = await shareDeepseek(page);
  else raw = await shareGeneric(page, profile);
  const hooked = await page.evaluate(() => window.__geoShare || '').catch(() => '');
  const clip = await readClipboard(page);
  return publicShareUrl(raw) || publicShareUrl(hooked) || publicShareUrl(clip);
}

export function publicShareUrl(value) {
  const text = String(value || '').trim();
  const match = text.match(/https?:\/\/[^\s"'<>]+/);
  if (!match) return '';
  const clean = match[0].replace(/[),.;，。]+$/, '');
  let parsed;
  try {
    parsed = new URL(clean);
  } catch {
    return '';
  }
  const host = parsed.hostname;
  const path = parsed.pathname;
  if (host.endsWith('doubao.com')) {
    return /^\/thread\/[^/]+/.test(path) ? `${parsed.origin}${path}` : '';
  }
  if (host.includes('deepseek.com')) {
    return /^\/share\/[^/]+/.test(path) ? `${parsed.origin}${path}` : '';
  }
  if (/\/a\/chat\/s\//.test(path) || /\/chat\/\d+/.test(path)) return '';
  return clean;
}

async function shareDoubao(page) {
  const share = page.locator("[data-testid='message_action_share']");
  const visibleShare = share.filter({ visible: true });
  const shareBtn = (await visibleShare.count().catch(() => 0)) ? visibleShare.last() : share.last();
  await shareBtn.scrollIntoViewIfNeeded().catch(() => {});
  await shareBtn.click({ timeout: 8000 });
  await page.waitForTimeout(600);
  const copy = page.locator("[data-testid='thread_share_copy_btn']").filter({ visible: true }).first();
  await copy.click({ timeout: 8000 });
  await page.waitForTimeout(400);
  const url = await readClipboard(page);
  await page.getByRole('button', { name: '取消' }).filter({ visible: true }).first().click({ timeout: 2000 }).catch(() => {});
  return url;
}

async function shareDeepseek(page) {
  await page.locator('[class*="markdown"], [data-message-id]').last().hover().catch(() => {});
  const share = page
    .locator("[aria-label*='分享'], [title*='分享'], [data-testid*='share']")
    .filter({ visible: true })
    .last();
  if (await share.count().catch(() => 0)) {
    await share.click({ timeout: 4000 }).catch(() => {});
  } else {
    await page.getByRole('button', { name: /分享|Share/ }).last().click({ timeout: 4000 }).catch(() => {});
  }
  await page.waitForTimeout(500);
  await clickVisibleText(page, /^创建分享链接$/);
  await page.waitForTimeout(700);
  await clickVisibleText(page, /^创建并复制$/);
  await page.waitForTimeout(500);
  const fromUi = publicShareUrl(await page.evaluate(() => window.__geoShare || '').catch(() => ''))
    || publicShareUrl(await readClipboard(page));
  if (fromUi) return fromUi;
  return page.evaluate(createDeepseekShare).catch(() => '');
}

async function shareGeneric(page, profile) {
  if (!profile.shareSelector) return '';
  try {
    await page.locator(profile.shareSelector).filter({ visible: true }).last().click({ timeout: 4000 });
    await page.waitForTimeout(800);
    await clickVisibleText(page, /创建分享链接|复制链接|创建并复制|复制分享链接/);
    await page.waitForTimeout(500);
    const box = page.locator(profile.shareLinkSelector || "input[value^='http']").first();
    if (await box.count()) {
      const value = await box.inputValue().catch(() => '');
      if (value.startsWith('http')) return value;
      const href = await box.getAttribute('href').catch(() => '');
      if (href && href.startsWith('http')) return href;
    }
  } catch {
    // 分享入口各平台不一样，拿不到公开链接就留空
  }
  return '';
}

async function clickVisibleText(page, pattern) {
  const loc = page.getByText(pattern).filter({ visible: true }).last();
  if (!(await loc.count().catch(() => 0))) return false;
  await loc.click({ timeout: 4000 }).catch(() => {});
  return true;
}

async function readClipboard(page) {
  return page.evaluate(() => navigator.clipboard.readText().catch(() => '')).catch(() => '');
}

async function installShareHook(page) {
  await page.evaluate(() => {
    if (window.__geoShareHook) return;
    window.__geoShareHook = true;
    window.__geoShare = '';
    const clip = navigator.clipboard;
    if (clip && clip.writeText) {
      const orig = clip.writeText.bind(clip);
      clip.writeText = (text) => {
        const value = String(text || '');
        if (/https?:\/\//.test(value)) window.__geoShare = value;
        return orig(text).catch(() => undefined);
      };
    }
    const origFetch = window.fetch.bind(window);
    window.fetch = async (...args) => {
      const response = await origFetch(...args);
      try {
        const input = args[0];
        const raw = typeof input === 'string' ? input : input && input.url;
        if (raw && String(raw).includes('/share/create')) {
          const data = await response.clone().json();
          const found = JSON.stringify(data).match(/"share_id"\s*:\s*"([^"]+)"/);
          if (found) window.__geoShare = `${location.origin}/share/${found[1]}`;
        }
      } catch {
        // 分享应答解析失败时仍走剪贴板
      }
      return response;
    };
  }).catch(() => {});
}

function hostname(url) {
  try {
    return new URL(url).hostname;
  } catch {
    return '';
  }
}

async function createDeepseekShare() {
  const session = (location.pathname.match(/\/a\/chat\/s\/([^/?#]+)/) || [])[1];
  if (!session) return '';
  const headers = { 'content-type': 'application/json' };
  const token = localStorage.getItem('userToken');
  if (token) headers.authorization = `Bearer ${token}`;
  const historyRes = await fetch(`/api/v0/chat/history_messages?chat_session_id=${encodeURIComponent(session)}`, {
    credentials: 'include',
    headers,
  });
  const history = await historyRes.json();
  const lists = [];
  const walk = (node) => {
    if (!node || typeof node !== 'object') return;
    if (Array.isArray(node)) {
      if (node.length && node.every((item) => item && typeof item === 'object' && (item.message_id || item.id))) {
        lists.push(node.map((item) => String(item.message_id || item.id)));
      }
      node.forEach(walk);
      return;
    }
    Object.values(node).forEach(walk);
  };
  walk(history);
  const ids = lists.sort((a, b) => b.length - a.length)[0] || [];
  const paired = ids.slice(0, ids.length - (ids.length % 2));
  if (!paired.length) return '';
  const createRes = await fetch('/api/v0/share/create', {
    method: 'POST',
    credentials: 'include',
    headers,
    body: JSON.stringify({ chat_session_id: session, message_ids: paired }),
  });
  const created = await createRes.json();
  const found = JSON.stringify(created).match(/"share_id"\s*:\s*"([^"]+)"/);
  return found ? `https://chat.deepseek.com/share/${found[1]}` : '';
}
