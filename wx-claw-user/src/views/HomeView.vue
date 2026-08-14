<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

const features = [
  { index: '01', title: 'Agent 编排', desc: 'LLM 自动拆解任务：规划 → 工具调用 → 校验 → 重试 → 降级，复杂请求也能多步完成。' },
  { index: '02', title: '多模态回复', desc: '图片生成、语音合成、视频生成，一次对话可同时返回文本与多种媒体附件。' },
  { index: '03', title: '多轮记忆', desc: '滑动窗口 + 摘要压缩 + 向量记忆，长期记住用户偏好，自动沉淀用户画像。' },
  { index: '04', title: '多服务商模型', desc: 'DeepSeek、OpenAI、火山方舟、SiliconFlow 自由切换，Key 与模型由用户自助配置。' },
  { index: '05', title: '租户级隔离', desc: '每个租户独立 API Key 与数据空间，PBKDF2 哈希存储，控制台权限按 Scope 收敛。' },
  { index: '06', title: '工具全家桶', desc: '联网搜索、天气、定时提醒、知识库问答、简历分析，微信里一句话即可调用。' },
]

const steps = [
  { index: 'STEP 01', title: '注册租户', desc: '填写租户信息，立即获得控制台 API Key（仅展示一次）。' },
  { index: 'STEP 02', title: '创建 Bot 扫码', desc: '在控制台创建微信 Bot，扫码完成 iLink 连接，状态实时可见。' },
  { index: 'STEP 03', title: '直接对话', desc: '你的微信好友发消息即可触发 AI 助手，聊天记录自动归档。' },
]

const terminalLines = [
  { kind: 'in', text: '[ILINK] 收到消息: "帮我生成一张图片"' },
  { kind: 'sys', text: '[PLAN]  拆解任务 → tool=image_generate' },
  { kind: 'ok', text: '[TOOL]  image_generate 完成 (2.1s)' },
  { kind: 'out', text: '[REPLY] 已发送图片到会话 ✔' },
]

const cursorVisible = ref(true)
let cursorTimer: number | undefined
let observer: IntersectionObserver | undefined

onMounted(() => {
  cursorTimer = window.setInterval(() => {
    cursorVisible.value = !cursorVisible.value
  }, 530)
  observer = new IntersectionObserver(entries => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        entry.target.classList.add('is-visible')
        observer?.unobserve(entry.target)
      }
    })
  }, { threshold: 0.12 })
  document.querySelectorAll('[data-reveal]').forEach(el => observer?.observe(el))
})

onBeforeUnmount(() => {
  if (cursorTimer) window.clearInterval(cursorTimer)
  observer?.disconnect()
})
</script>

<template>
  <div class="home">
    <a class="skip-link" href="#main">跳到主要内容</a>

    <header class="site-header">
      <div class="header-inner">
        <a class="brand" href="/">
          <span class="brand-mark">WX</span>
          <span class="brand-name">CLAW</span>
        </a>
        <nav class="site-nav" aria-label="主导航">
          <a href="#features">功能</a>
          <a href="#how">使用流程</a>
          <a href="#faq">FAQ</a>
        </nav>
        <div class="header-actions">
          <router-link class="btn btn-ghost" to="/login">登录</router-link>
          <router-link class="btn btn-accent" to="/register">免费注册</router-link>
        </div>
      </div>
    </header>

    <main id="main">
      <section class="hero">
        <div class="hero-grid" aria-hidden="true"></div>
        <div class="hero-inner">
          <div class="hero-copy">
            <p class="kicker"><span class="kicker-dot"></span> WECHAT × AI AGENT</p>
            <h1 class="hero-title">
              把 AI 助手
              <span class="hero-accent">接进你的微信</span>
            </h1>
            <p class="hero-sub">
              WX-Claw 是一个微信 ILink 智能体平台：注册即得独立控制台，
              创建 Bot 扫码连接后，你的用户直接发微信就能对话、生图、语音、查资料。
            </p>
            <div class="hero-cta">
              <router-link class="btn btn-accent btn-lg" to="/register">免费注册租户</router-link>
              <router-link class="btn btn-ghost btn-lg" to="/login">已有 Key，去登录</router-link>
            </div>
            <dl class="hero-stats">
              <div class="stat"><dt>接入方式</dt><dd>微信 ILink 扫码</dd></div>
              <div class="stat"><dt>回复形态</dt><dd>文本 / 图片 / 语音 / 视频</dd></div>
              <div class="stat"><dt>模型服务商</dt><dd>4+ 自由切换</dd></div>
            </dl>
          </div>

          <div class="terminal" role="img" aria-label="消息处理流水线演示">
            <div class="terminal-bar">
              <span class="terminal-dot red"></span>
              <span class="terminal-dot yellow"></span>
              <span class="terminal-dot green"></span>
              <span class="terminal-title">wx-claw / agent pipeline</span>
            </div>
            <div class="terminal-body">
              <template v-for="(line, i) in terminalLines" :key="i">
                <p class="tline" :class="`t-${line.kind}`" :data-reveal="''">{{ line.text }}</p>
              </template>
              <p class="tline t-cursor"><span :class="{ 'cursor-off': !cursorVisible }">▌</span></p>
            </div>
            <div class="terminal-foot">
              <span>status: <b class="ok">RUNNING</b></span>
              <span>tenant: default</span>
              <span class="mono">uptime 99.9%</span>
            </div>
          </div>
        </div>
      </section>

      <section id="features" class="section features">
        <div class="section-head" data-reveal="">
          <p class="kicker"><span class="kicker-dot"></span> CAPABILITIES</p>
          <h2>一个助手，六项硬能力</h2>
          <p class="section-sub">从消息进来到回复发出，整条链路由 Agent 编排系统接管。</p>
        </div>
        <div class="feature-grid">
          <article v-for="f in features" :key="f.index" class="feature-card" data-reveal="">
            <span class="feature-index mono">{{ f.index }}</span>
            <h3>{{ f.title }}</h3>
            <p>{{ f.desc }}</p>
          </article>
        </div>
      </section>

      <section id="how" class="section how">
        <div class="section-head" data-reveal="">
          <p class="kicker"><span class="kicker-dot"></span> HOW IT WORKS</p>
          <h2>三步上线，无需部署</h2>
        </div>
        <ol class="step-rail">
          <li v-for="(s, i) in steps" :key="s.index" class="step" data-reveal="">
            <span class="step-index mono">{{ s.index }}</span>
            <span class="step-num mono">{{ `0${i + 1}` }}</span>
            <div class="step-body">
              <h3>{{ s.title }}</h3>
              <p>{{ s.desc }}</p>
            </div>
          </li>
        </ol>
      </section>

      <section id="faq" class="section faq">
        <div class="section-head" data-reveal="">
          <p class="kicker"><span class="kicker-dot"></span> FAQ</p>
          <h2>常见问题</h2>
        </div>
        <div class="faq-list">
          <details class="faq-item" data-reveal="">
            <summary>注册后如何登录控制台？</summary>
            <p>注册成功会返回一个 API Key（credentialId.secret 格式），只展示一次。用它即可登录控制台；忘记后可在设置页重新生成。</p>
          </details>
          <details class="faq-item" data-reveal="">
            <summary>机器人如何接入我的微信？</summary>
            <p>控制台创建 Bot 后会出现二维码，用微信扫码完成 ILink 连接，连接状态与断线重连情况都会实时展示。</p>
          </details>
          <details class="faq-item" data-reveal="">
            <summary>不同租户的数据会互相隔离吗？</summary>
            <p>会。所有业务数据以租户为根隔离，API Key 使用 PBKDF2 哈希存储，控制台权限按 Scope 收敛。</p>
          </details>
          <details class="faq-item" data-reveal="">
            <summary>模型和 API Key 必须用平台的默认配置吗？</summary>
            <p>不用。设置页支持为对话、图片、语音、视频分别配置服务商、模型与自己的 API Key。</p>
          </details>
        </div>
      </section>

      <section class="cta-band" data-reveal="">
        <p class="cta-kicker mono">READY TO DEPLOY?</p>
        <h2>准备好把 AI 接进微信了吗？</h2>
        <p>注册免费，一分钟创建你的第一个微信 Bot。</p>
        <router-link class="btn btn-accent btn-lg" to="/register">立即免费注册</router-link>
      </section>
    </main>

    <footer class="site-footer">
      <div class="footer-inner">
        <div>
          <span class="brand-name">WX-CLAW</span>
          <p class="muted">微信 ILink 智能体平台 · Agent 编排 · 多模态 · 多租户</p>
        </div>
        <nav class="footer-nav" aria-label="页脚导航">
          <router-link to="/login">登录</router-link>
          <router-link to="/register">注册</router-link>
          <a href="#top">返回顶部</a>
        </nav>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.home {
  --bg: #0b0d10;
  --bg-soft: #11141a;
  --panel: #151922;
  --line: rgba(255, 255, 255, 0.1);
  --fg: #f2f4f8;
  --muted: #9aa3b2;
  --accent: #ffb400;
  --accent-2: #2de1c2;
  --ok: #2de1c2;
  --radius: 14px;
  --ease: cubic-bezier(0.22, 1, 0.36, 1);
  background: var(--bg);
  color: var(--fg);
  min-height: 100vh;
  font-family: Inter, "PingFang SC", "Microsoft YaHei", sans-serif;
}
.mono { font-family: "JetBrains Mono", Consolas, monospace; }
.muted { color: var(--muted); }

.skip-link {
  position: absolute;
  left: -999px;
  top: 8px;
  z-index: 100;
  padding: 10px 16px;
  background: var(--accent);
  color: #14161a;
  font-weight: 700;
  border-radius: 8px;
}
.skip-link:focus { left: 8px; }

.site-header {
  position: sticky;
  top: 0;
  z-index: 50;
  background: rgba(11, 13, 16, 0.82);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--line);
}
.header-inner {
  max-width: 1160px;
  margin: 0 auto;
  padding: 0 24px;
  height: 64px;
  display: flex;
  align-items: center;
  gap: 28px;
}
.brand { display: flex; align-items: center; gap: 8px; text-decoration: none; color: var(--fg); }
.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  background: var(--accent);
  color: #14161a;
  font-weight: 800;
  font-size: 13px;
  border-radius: 8px;
  letter-spacing: 0.5px;
}
.brand-name { font-weight: 800; letter-spacing: 2px; font-size: 15px; }
.site-nav { display: flex; gap: 24px; flex: 1; }
.site-nav a { color: var(--muted); text-decoration: none; font-size: 14px; transition: color 180ms; }
.site-nav a:hover { color: var(--fg); }
.header-actions { display: flex; gap: 10px; }

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 9px 18px;
  border-radius: 9px;
  font-size: 14px;
  font-weight: 700;
  text-decoration: none;
  border: 1px solid transparent;
  cursor: pointer;
  transition: transform 180ms var(--ease), box-shadow 180ms var(--ease), background 180ms;
}
.btn:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
.btn-accent { background: var(--accent); color: #14161a; }
.btn-accent:hover { transform: translateY(-1px); box-shadow: 0 8px 24px rgba(255, 180, 0, 0.25); }
.btn-ghost { border-color: var(--line); color: var(--fg); background: transparent; }
.btn-ghost:hover { border-color: var(--accent); }
.btn-lg { padding: 13px 26px; font-size: 15px; border-radius: 10px; }

.hero { position: relative; overflow: hidden; }
.hero-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.045) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.045) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: radial-gradient(ellipse 90% 70% at 50% 20%, #000 40%, transparent 75%);
}
.hero-inner {
  position: relative;
  max-width: 1160px;
  margin: 0 auto;
  padding: 92px 24px 96px;
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  gap: 56px;
  align-items: center;
}
.kicker {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 16px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 2px;
  color: var(--accent-2);
}
.kicker-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--accent); box-shadow: 0 0 10px var(--accent); }
.hero-title {
  margin: 0;
  font-size: clamp(34px, 5vw, 58px);
  line-height: 1.12;
  letter-spacing: -1px;
  font-weight: 800;
}
.hero-accent {
  display: block;
  color: var(--accent);
  text-decoration: underline wavy rgba(255, 180, 0, 0.45);
  text-underline-offset: 8px;
}
.hero-sub { margin: 22px 0 0; color: var(--muted); font-size: 16px; line-height: 1.75; max-width: 46ch; }
.hero-cta { display: flex; gap: 12px; margin-top: 30px; flex-wrap: wrap; }
.hero-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;
  margin: 42px 0 0;
  border-top: 1px solid var(--line);
  padding-top: 24px;
}
.stat dt { font-size: 12px; color: var(--muted); margin-bottom: 4px; }
.stat dd { margin: 0; font-size: 13px; font-weight: 700; color: var(--fg); }

.terminal {
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.03), transparent 30%), var(--panel);
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.5);
  overflow: hidden;
  position: relative;
}
.terminal::before {
  content: "";
  position: absolute;
  inset: 0;
  background: repeating-linear-gradient(90deg, rgba(255, 255, 255, 0.02), rgba(255, 255, 255, 0.02) 1px, transparent 1px, transparent 3px);
  pointer-events: none;
}
.terminal-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.02);
}
.terminal-dot { width: 10px; height: 10px; border-radius: 50%; }
.terminal-dot.red { background: #ff5f57; }
.terminal-dot.yellow { background: #febc2e; }
.terminal-dot.green { background: #28c840; }
.terminal-title { margin-left: 8px; font-size: 12px; color: var(--muted); font-family: "JetBrains Mono", Consolas, monospace; }
.terminal-body { padding: 20px 18px 12px; min-height: 220px; display: flex; flex-direction: column; gap: 14px; }
.tline {
  margin: 0;
  font-family: "JetBrains Mono", Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
  opacity: 0;
  transform: translateY(10px);
  transition: opacity 420ms var(--ease), transform 420ms var(--ease);
}
.tline.is-visible { opacity: 1; transform: none; }
.t-in { color: var(--fg); }
.t-sys { color: var(--accent-2); }
.t-ok { color: var(--ok); }
.t-out { color: var(--accent); }
.t-cursor { color: var(--accent); opacity: 1; transform: none; }
.cursor-off { opacity: 0; }
.terminal-foot {
  display: flex;
  gap: 18px;
  padding: 10px 16px;
  border-top: 1px solid var(--line);
  font-family: "JetBrains Mono", Consolas, monospace;
  font-size: 11px;
  color: var(--muted);
}
.ok { color: var(--ok); }

.section { max-width: 1160px; margin: 0 auto; padding: 88px 24px; }
.section-head { max-width: 640px; margin-bottom: 44px; }
.section-head h2 { margin: 0 0 12px; font-size: clamp(26px, 3.4vw, 38px); letter-spacing: -0.5px; }
.section-sub { margin: 0; color: var(--muted); line-height: 1.7; }

.feature-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 18px; }
.feature-card {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.025), transparent), var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 24px;
  position: relative;
  transition: transform 220ms var(--ease), border-color 220ms;
}
.feature-card:hover { transform: translateY(-4px); border-color: rgba(255, 180, 0, 0.45); }
.feature-card::before {
  content: "";
  position: absolute;
  top: 0; left: 24px; right: 24px;
  height: 2px;
  background: linear-gradient(90deg, var(--accent), transparent);
  border-radius: 2px;
}
.feature-index { font-size: 12px; color: var(--accent); letter-spacing: 1px; }
.feature-card h3 { margin: 14px 0 8px; font-size: 17px; }
.feature-card p { margin: 0; color: var(--muted); font-size: 13px; line-height: 1.7; }

.how { border-top: 1px solid var(--line); }
.step-rail { list-style: none; margin: 0; padding: 0; display: grid; gap: 14px; }
.step {
  display: grid;
  grid-template-columns: 130px 44px 1fr;
  gap: 20px;
  align-items: start;
  padding: 24px;
  border: 1px solid var(--line);
  border-left: 3px solid var(--accent);
  border-radius: var(--radius);
  background: var(--panel);
}
.step-index { font-size: 11px; letter-spacing: 2px; color: var(--accent-2); padding-top: 4px; }
.step-num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border: 1px solid var(--line);
  border-radius: 10px;
  font-size: 14px;
  color: var(--accent);
  background: rgba(255, 180, 0, 0.07);
}
.step-body h3 { margin: 0 0 6px; font-size: 16px; }
.step-body p { margin: 0; color: var(--muted); font-size: 13px; line-height: 1.7; }

.faq { border-top: 1px solid var(--line); }
.faq-list { display: grid; gap: 12px; max-width: 820px; }
.faq-item { border: 1px solid var(--line); border-radius: 12px; background: var(--panel); padding: 0 18px; }
.faq-item summary {
  cursor: pointer;
  padding: 16px 0;
  font-weight: 700;
  font-size: 15px;
  list-style: none;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.faq-item summary::-webkit-details-marker { display: none; }
.faq-item summary::after { content: "+"; color: var(--accent); font-size: 20px; }
.faq-item[open] summary::after { content: "−"; }
.faq-item p { margin: 0 0 16px; color: var(--muted); font-size: 13px; line-height: 1.7; }

.cta-band {
  text-align: center;
  padding: 88px 24px;
  background:
    radial-gradient(ellipse 60% 90% at 50% 120%, rgba(255, 180, 0, 0.16), transparent 60%),
    linear-gradient(180deg, rgba(255, 180, 0, 0.04), transparent 40%);
  border-top: 1px solid var(--line);
}
.cta-kicker { margin: 0 0 14px; font-size: 12px; letter-spacing: 3px; color: var(--accent); }
.cta-band h2 { margin: 0 0 12px; font-size: clamp(26px, 3.4vw, 38px); }
.cta-band p { margin: 0 0 28px; color: var(--muted); }

.site-footer { border-top: 1px solid var(--line); }
.footer-inner {
  max-width: 1160px;
  margin: 0 auto;
  padding: 32px 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
}
.footer-inner p { margin: 6px 0 0; font-size: 12px; }
.footer-nav { display: flex; gap: 18px; }
.footer-nav a { color: var(--muted); text-decoration: none; font-size: 13px; }
.footer-nav a:hover { color: var(--accent); }

[data-reveal] { opacity: 0; transform: translateY(18px); transition: opacity 560ms var(--ease), transform 560ms var(--ease); }
[data-reveal].is-visible { opacity: 1; transform: none; }

@media (max-width: 900px) {
  .hero-inner { grid-template-columns: 1fr; padding-top: 64px; }
  .feature-grid { grid-template-columns: 1fr 1fr; }
  .site-nav { display: none; }
}
@media (max-width: 640px) {
  .feature-grid { grid-template-columns: 1fr; }
  .hero-stats { grid-template-columns: 1fr; gap: 12px; }
  .step { grid-template-columns: 1fr; gap: 10px; }
  .header-actions .btn-ghost { display: none; }
}
@media (prefers-reduced-motion: reduce) {
  [data-reveal], .tline { opacity: 1; transform: none; transition: none; }
  .btn, .feature-card { transition: none; }
}
</style>
