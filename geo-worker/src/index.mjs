import { chromium } from 'playwright';
import { createInterface } from 'node:readline';
import { existsSync, mkdirSync, readFileSync, unlinkSync, writeFileSync } from 'node:fs';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const configPath = path.join(root, 'config.json');
const mode = process.argv[2] === 'login' ? 'login' : process.argv[2] === 'check' ? 'check' : 'run';

const config = loadConfig();
const state = { jobId: null, accountId: null };

function loadConfig() {
  if (!existsSync(configPath)) {
    throw new Error('缺少 config.json，请先复制 config.example.json');
  }
  const raw = JSON.parse(readFileSync(configPath, 'utf8'));
  if (!raw.baseUrl || !raw.token || !raw.node) {
    throw new Error('config.json 需要 baseUrl、token、node');
  }
  raw.baseUrl = String(raw.baseUrl).replace(/\/$/, '');
  raw.profileRoot = raw.profileRoot || path.join(root, 'profiles');
  raw.pollMs = Number(raw.pollMs) > 0 ? Number(raw.pollMs) : 5000;
  mkdirSync(raw.profileRoot, { recursive: true });
  mkdirSync(path.join(root, 'shots'), { recursive: true });
  return raw;
}

function takeLock() {
  const lockFile = path.join(config.profileRoot, '.worker.lock');
  if (existsSync(lockFile)) {
    const pid = Number(readFileSync(lockFile, 'utf8'));
    if (pid && pid !== process.pid && pidAlive(pid)) {
      throw new Error(`已有采集进程在运行（pid ${pid}）。不要开第二个，否则 Chrome 用户目录会冲突。`);
    }
  }
  writeFileSync(lockFile, String(process.pid));
  const release = () => {
    try {
      if (existsSync(lockFile) && readFileSync(lockFile, 'utf8').trim() === String(process.pid)) {
        unlinkSync(lockFile);
      }
    } catch {
      // 进程退出时锁文件删不掉不影响下次启动判断
    }
  };
  process.on('exit', release);
  process.on('SIGINT', () => {
    release();
    process.exit(0);
  });
}

function pidAlive(pid) {
  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
}

async function api(pathname, { json, form } = {}) {
  const response = await fetch(config.baseUrl + pathname, {
    method: 'POST',
    headers: {
      'X-Geo-Worker-Token': config.token,
      ...(json ? { 'Content-Type': 'application/json' } : {}),
    },
    body: form || JSON.stringify(json || {}),
  });
  const body = await response.json().catch(() => ({}));
  if (response.status === 401 || response.status === 404) {
    const error = new Error('测试环境还没有本机采集接口。需要先把这次改动发布到测试 ECS，服务起来后再执行。');
    error.fatal = true;
    throw error;
  }
  if (body.code !== 200) {
    const error = new Error(body.msg || `请求失败 HTTP ${response.status}`);
    error.code = body.code;
    error.fatal = body.code === 401;
    throw error;
  }
  return body.data;
}

async function heartbeat() {
  await api('/geo/auto/worker/heartbeat', {
    json: {
      node: config.node,
      version: '1',
      currentJobId: state.jobId,
      currentAccountId: state.accountId,
    },
  });
}

function launchOptions() {
  const options = {
    headless: false,
    viewport: { width: 1440, height: 900 },
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    args: ['--disable-blink-features=AutomationControlled'],
  };
  if (config.executablePath) {
    options.executablePath = config.executablePath;
  } else {
    options.channel = 'chrome';
  }
  return options;
}

function profileDir(accountId) {
  const dir = path.join(config.profileRoot, String(accountId));
  mkdirSync(dir, { recursive: true });
  return dir;
}

function waitEnter(prompt) {
  const rl = createInterface({ input: process.stdin, output: process.stdout });
  return new Promise((resolve) => {
    rl.question(prompt, () => {
      rl.close();
      resolve();
    });
  });
}

async function loginAll() {
  await heartbeat();
  const accounts = await api('/geo/auto/worker/accounts', { json: { node: config.node } });
  if (!accounts?.length) {
    console.log('这台电脑没有可登录的账号。请在平台账号里打开浏览器监测，登录方式选「本机 Chrome」，采集电脑填 ' + config.node);
    return;
  }
  for (const account of accounts) {
    if (!account.url) {
      console.log(`跳过 ${account.platform} ${account.accountLabel}：服务器没有配置提问页地址`);
      continue;
    }
    console.log(`打开 ${account.platform} ${account.accountLabel}（当前状态 ${account.loginStatus}）`);
    const context = await chromium.launchPersistentContext(profileDir(account.accountId), launchOptions());
    try {
      const page = context.pages()[0] || (await context.newPage());
      await page.goto(account.url, { waitUntil: 'domcontentloaded', timeout: 45000 });
      await waitEnter('在窗口里完成登录后，回到这里按回车...');
      await api('/geo/auto/worker/account-status', {
        json: { node: config.node, accountId: account.accountId, status: 'READY' },
      });
      console.log(`已标记就绪：${account.platform} ${account.accountLabel}`);
    } finally {
      await context.close().catch(() => {});
    }
  }
}

async function check() {
  await heartbeat();
  const accounts = await api('/geo/auto/worker/accounts', { json: { node: config.node } });
  console.log(`服务器已接通，电脑名 ${config.node}，分配到 ${(accounts || []).length} 个账号`);
}

async function runLoop() {
  setInterval(() => {
    heartbeat().catch((error) => console.error('心跳失败：' + error.message));
  }, 20000);
  await heartbeat();
  console.log(`采集已启动，电脑名 ${config.node}，等待题目`);
  for (;;) {
    let job = null;
    try {
      job = await api('/geo/auto/worker/claim', { json: { node: config.node } });
    } catch (error) {
      console.error('领取失败：' + error.message);
      if (error.fatal || error.code === 401) process.exit(1);
      await sleep(config.pollMs);
      continue;
    }
    if (!job?.jobId) {
      await sleep(config.pollMs);
      continue;
    }
    state.jobId = job.jobId;
    state.accountId = job.accountId;
    console.log(`领取 ${job.platform} #${job.jobId}：${job.question}`);
    try {
      await ask(job);
    } catch (error) {
      console.error(`提问异常 #${job.jobId}：${error.message}`);
      await api('/geo/auto/worker/fail', {
        json: { node: config.node, jobId: job.jobId, reason: 'ERROR', message: error.message },
      }).catch((failError) => console.error('回报失败：' + failError.message));
    } finally {
      state.jobId = null;
      state.accountId = null;
    }
    await sleep(3000 + Math.floor(Math.random() * 5000));
  }
}

async function ask(job) {
  const profile = job.profile || {};
  const context = await chromium.launchPersistentContext(profileDir(job.accountId), launchOptions());
  try {
    const page = context.pages()[0] || (await context.newPage());
    await page.goto(profile.url, { waitUntil: 'domcontentloaded', timeout: job.navigationTimeoutMs || 45000 });
    await page.waitForTimeout(1200);
    await dismiss(page, profile.dismissSelectors);
    if (await loginWall(page, profile.loginRequiredSelectors)) {
      await api('/geo/auto/worker/fail', {
        json: {
          node: config.node,
          jobId: job.jobId,
          reason: 'NEED_LOGIN',
          message: `${job.platform} 出现登录或验证，请在这台电脑重新执行 npm run login`,
        },
      });
      console.log(`${job.platform} 需要重新登录，本题已放回队列`);
      return;
    }
    if (job.deepThink && profile.deepThinkSelector) {
      await page.locator(profile.deepThinkSelector).first().click({ timeout: 4000 }).catch(() => {});
    }
    const input = page.locator(profile.inputSelector || 'textarea').first();
    await input.click({ timeout: 15000 });
    await page.keyboard.type(job.question, { delay: 35 + Math.floor(Math.random() * 45) });
    await page.waitForTimeout(400);
    if (profile.submitSelector) {
      await page.locator(profile.submitSelector).first().click({ timeout: 8000 });
    } else {
      await page.keyboard.press('Enter');
    }
    const answer = await waitAnswer(page, profile, job.answerTimeoutMs || 120000);
    if (!answer || rejected(answer, profile.answerRejectPatterns)) {
      throw new Error(answer ? '页面文本不是回答' : '等待回答超时');
    }
    const reasoning = profile.reasoningContentSelector
      ? await textOf(page, profile.reasoningContentSelector)
      : '';
    const citations = await collectCitations(page, profile.citationSelector);
    const probeUrl = await shareLink(page, profile);
    let shot = '';
    if (job.screenshot) {
      shot = path.join(root, 'shots', `${job.jobId}.jpg`);
      await page.screenshot({ path: shot, fullPage: true, type: 'jpeg', quality: 60 });
      const size = (await readFile(shot)).length;
      if (size > 8 * 1024 * 1024) {
        await page.screenshot({ path: shot, fullPage: false, type: 'jpeg', quality: 50 });
      }
    }
    await upload(job, { answer, reasoning, probeUrl, citations, shot });
    console.log(`已回传 #${job.jobId}，回答 ${answer.length} 字`);
  } finally {
    await context.close().catch(() => {});
  }
}

async function upload(job, payload) {
  const form = new FormData();
  form.set('node', config.node);
  form.set('jobId', String(job.jobId));
  form.set('answerText', payload.answer);
  form.set('reasoningText', payload.reasoning || '');
  form.set('probeUrl', payload.probeUrl || '');
  form.set('citationsJson', JSON.stringify(payload.citations || []));
  form.set('diagnostic', `edge profile ${config.node}`);
  if (payload.shot) {
    const bytes = await readFile(payload.shot);
    form.set('screenshot', new Blob([bytes], { type: 'image/jpeg' }), 'page.jpg');
  }
  await api('/geo/auto/worker/result', { form });
}

async function waitAnswer(page, profile, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  let last = '';
  let stable = 0;
  let answer = '';
  while (Date.now() < deadline) {
    const streaming = profile.streamingSelector
      ? await page.locator(profile.streamingSelector).count().catch(() => 0)
      : 0;
    answer = await textOf(page, profile.answerSelector);
    if (!streaming && answer && answer === last) stable += 1;
    else stable = 0;
    last = answer;
    if (stable >= 3 && answer.length > 30) return answer;
    await page.waitForTimeout(1000);
  }
  return answer;
}

async function textOf(page, selector) {
  if (!selector) return '';
  const loc = page.locator(selector).last();
  if (!(await loc.count().catch(() => 0))) return '';
  return ((await loc.innerText().catch(() => '')) || '').trim();
}

async function dismiss(page, selectors) {
  for (const sel of selectors || []) {
    const loc = page.locator(sel).first();
    if (await loc.count().catch(() => 0)) {
      if (await loc.isVisible().catch(() => false)) {
        await loc.click({ timeout: 1500 }).catch(() => {});
      }
    }
  }
}

async function loginWall(page, selectors) {
  for (const sel of selectors || []) {
    const loc = page.locator(sel).first();
    if ((await loc.count().catch(() => 0)) && (await loc.isVisible().catch(() => false))) {
      return true;
    }
  }
  return false;
}

function rejected(answer, patterns) {
  return (patterns || []).some((item) => item && answer.includes(item) && answer.length < item.length + 80);
}

async function collectCitations(page, selector) {
  const sel = selector || "a[href^='http']";
  const links = await page.locator(sel).evaluateAll((nodes) =>
    nodes.slice(0, 20).map((node) => ({
      title: (node.innerText || node.getAttribute('title') || '').trim().slice(0, 200),
      url: node.href || '',
    })),
  ).catch(() => []);
  const seen = new Set();
  return links.filter((item) => item.url && !seen.has(item.url) && seen.add(item.url));
}

async function shareLink(page, profile) {
  if (!profile.shareSelector) return page.url();
  try {
    await page.locator(profile.shareSelector).last().click({ timeout: 4000 });
    await page.waitForTimeout(800);
    const box = page.locator(profile.shareLinkSelector || "input[value^='http']").first();
    if (await box.count()) {
      const value = await box.inputValue().catch(() => '');
      if (value.startsWith('http')) return value;
      const href = await box.getAttribute('href').catch(() => '');
      if (href && href.startsWith('http')) return href;
    }
  } catch {
    // 分享按钮各平台不一样，拿不到就留当前页地址
  }
  return page.url();
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

takeLock();
const task = mode === 'login' ? loginAll() : mode === 'check' ? check() : runLoop();
task.catch((error) => {
  console.error(error.message);
  process.exit(1);
});
