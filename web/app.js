'use strict'
/* ntvslide web preview.
 * Effective loudness of an app = master (system media) volume × that app's own volume,
 * which is exactly what the Android service applies on a real device. */

const SVG_NS = 'http://www.w3.org/2000/svg'

// ---------- dot-matrix rendering ----------
const FONT = { // 3×5 glyphs, one string per row
  0: '111101101101111', 1: '010110010010111', 2: '111001111100111', 3: '111001111001111',
  4: '101101111001001', 5: '111100111001111', 6: '111100111101111', 7: '111001001001001',
  8: '111101111101111', 9: '111101111001111', '%': '101001010100101',
  N: '111101101101101', M: '101111111101101', U: '101101101101111', T: '111010010010010', V: '101101101101010', S: '111100111001111',
  L: '100100100100111', I: '111010010010111', D: '110101101101110', E: '111100111100111'
}
const ICONS = { // 9×9 pictograms
  media: ['000010000', '000110000', '011110100', '011110010', '011110010', '011110100', '000110000', '000010000', '000000000'],
  music: ['000111110', '000111110', '000100010', '000100010', '000100010', '011100111', '111100111', '011000110', '000000000'],
  play: ['000000000', '010000000', '011000000', '011110000', '011111100', '011110000', '011000000', '010000000', '000000000'],
  chat: ['011111110', '100000001', '101010101', '100000001', '100000001', '011110110', '000010000', '000100000', '000000000'],
  game: ['000000000', '011111110', '111111111', '110111011', '101011101', '110111011', '111111111', '011000110', '000000000'],
  map: ['001111100', '011000110', '110010011', '110010011', '011000110', '001101100', '000111000', '000010000', '000000000']
}

function dotSvg (rows, { dot = 4, r = 1.5, color = 'currentColor' } = {}) {
  const h = rows.length
  const w = rows[0].length
  const svg = document.createElementNS(SVG_NS, 'svg')
  svg.setAttribute('width', w * dot)
  svg.setAttribute('height', h * dot)
  svg.setAttribute('viewBox', `0 0 ${w * dot} ${h * dot}`)
  svg.setAttribute('aria-hidden', 'true')
  rows.forEach((row, y) => [...row].forEach((c, x) => {
    if (c !== '1') return
    const el = document.createElementNS(SVG_NS, 'circle')
    el.setAttribute('cx', x * dot + dot / 2)
    el.setAttribute('cy', y * dot + dot / 2)
    el.setAttribute('r', r)
    el.setAttribute('fill', color)
    svg.appendChild(el)
  }))
  return svg
}

function textRows (str) {
  const rows = ['', '', '', '', '']
  ;[...String(str)].forEach((ch, i) => {
    const g = FONT[ch] || '000000000000000'
    for (let y = 0; y < 5; y++) rows[y] += (i ? '0' : '') + g.slice(y * 3, y * 3 + 3)
  })
  return rows
}

const dotText = (str, opts) => dotSvg(textRows(str), opts)

// ---------- state ----------
const DEFAULT_APPS = [
  { id: 'music', name: 'Music', icon: 'music', vol: 70, muted: false, wave: 'sine', notes: [220, 277.2, 329.6, 277.2] },
  { id: 'video', name: 'Video', icon: 'play', vol: 85, muted: false, wave: 'triangle', notes: [196, 246.9, 293.7, 392] },
  { id: 'chat', name: 'Chat', icon: 'chat', vol: 40, muted: false, wave: 'square', notes: [523.3, 659.3] },
  { id: 'game', name: 'Game', icon: 'game', vol: 55, muted: false, wave: 'sawtooth', notes: [130.8, 164.8, 196, 164.8] },
  { id: 'maps', name: 'Maps', icon: 'map', vol: 100, muted: false, wave: 'sine', notes: [440, 440, 587.3] }
]
const STORE_KEY = 'ntvslide.v1'

function loadState () {
  const base = { master: 60, masterMuted: false, apps: DEFAULT_APPS.map(a => ({ ...a, playing: false })) }
  try {
    const saved = JSON.parse(localStorage.getItem(STORE_KEY) || 'null')
    if (!saved) return base
    base.master = clamp(saved.master ?? base.master)
    base.masterMuted = !!saved.masterMuted
    base.apps.forEach(a => {
      const s = (saved.apps || {})[a.id]
      if (s) { a.vol = clamp(s.vol ?? a.vol); a.muted = !!s.muted }
    })
  } catch (e) { /* corrupt storage: fall back to defaults */ }
  return base
}
function saveState () {
  const apps = {}
  state.apps.forEach(a => { apps[a.id] = { vol: a.vol, muted: a.muted } })
  try { localStorage.setItem(STORE_KEY, JSON.stringify({ master: state.master, masterMuted: state.masterMuted, apps })) } catch (e) { /* storage unavailable */ }
}
const clamp = (v, lo = 0, hi = 100) => Math.min(hi, Math.max(lo, Math.round(Number(v) || 0)))
const state = loadState()

// ---------- audio engine (WebAudio; created on first gesture) ----------
const audio = {
  ctx: null,
  master: null,
  gains: {},
  timers: {},
  ensure () {
    if (this.ctx) return
    const Ctx = window.AudioContext || window.webkitAudioContext
    if (!Ctx) return
    this.ctx = new Ctx()
    this.master = this.ctx.createGain()
    this.master.connect(this.ctx.destination)
    state.apps.forEach(a => {
      const g = this.ctx.createGain()
      g.connect(this.master)
      this.gains[a.id] = g
    })
    this.apply()
  },
  apply () {
    if (!this.ctx) return
    const t = this.ctx.currentTime
    // squared taper feels closer to perceived loudness than linear; cap well below clipping
    const taper = v => Math.pow(v / 100, 2) * 0.5
    this.master.gain.setTargetAtTime(state.masterMuted ? 0 : taper(state.master), t, 0.02)
    state.apps.forEach(a => this.gains[a.id].gain.setTargetAtTime(a.muted ? 0 : taper(a.vol) * 2, t, 0.02))
  },
  start (app) {
    this.ensure()
    if (!this.ctx) return
    if (this.ctx.state === 'suspended') this.ctx.resume()
    let i = 0
    const tick = () => {
      const o = this.ctx.createOscillator()
      const env = this.ctx.createGain()
      const t = this.ctx.currentTime
      o.type = app.wave
      o.frequency.value = app.notes[i++ % app.notes.length]
      env.gain.setValueAtTime(0, t)
      env.gain.linearRampToValueAtTime(0.35, t + 0.02)
      env.gain.exponentialRampToValueAtTime(0.001, t + 0.42)
      o.connect(env).connect(this.gains[app.id])
      o.start(t)
      o.stop(t + 0.45)
    }
    tick()
    this.timers[app.id] = setInterval(tick, 450)
  },
  stop (app) {
    clearInterval(this.timers[app.id])
    delete this.timers[app.id]
  }
}

// ---------- slider component ----------
function createVolume ({ id, name, icon, master = false, get, setVol, toggleMute, extra }) {
  const col = document.createElement('div')
  col.className = 'vol'
  col.dataset.id = id

  const readout = document.createElement('div')
  readout.className = 'readout'

  const slider = document.createElement('div')
  slider.className = 'slider' + (master ? ' master' : '')
  slider.tabIndex = 0
  slider.setAttribute('role', 'slider')
  slider.setAttribute('aria-label', `${name} volume`)
  slider.setAttribute('aria-orientation', 'vertical')
  slider.setAttribute('aria-valuemin', '0')
  slider.setAttribute('aria-valuemax', '100')
  slider.innerHTML = '<div class="matrix"></div><div class="fill"></div>'
  const fill = slider.querySelector('.fill')

  const btn = document.createElement('button')
  btn.type = 'button'
  btn.className = 'icon-btn'
  btn.setAttribute('aria-label', `Mute ${name}`)
  const glyph = document.createElement('span')
  glyph.className = 'glyph'
  glyph.appendChild(dotSvg(ICONS[icon], { dot: 4, r: 1.5 }))
  btn.appendChild(glyph)
  btn.addEventListener('click', () => { toggleMute(); interact() })

  const label = document.createElement('div')
  label.className = 'name'
  label.textContent = name

  col.append(readout, slider, btn)
  if (extra) col.append(extra)
  col.append(label)

  function paint () {
    const { vol, muted } = get()
    // snap fill to whole dots so the matrix reads cleanly
    const dots = Math.round(vol / 100 * (slider.clientHeight / 8 || 28))
    fill.style.height = `${muted ? vol / 100 * 100 : (dots * 8 / (slider.clientHeight || 224)) * 100}%`
    slider.setAttribute('aria-valuenow', vol)
    slider.setAttribute('aria-valuetext', muted ? `${vol} percent, muted` : `${vol} percent`)
    col.classList.toggle('muted', muted)
    btn.setAttribute('aria-pressed', String(muted))
    readout.replaceChildren(dotText(muted ? 'MUTE' : vol, { dot: 3, r: 1.1 }))
  }

  const fromPointer = e => {
    const r = slider.getBoundingClientRect()
    return clamp((1 - (e.clientY - r.top) / r.height) * 100)
  }
  let lastTick = -1
  const set = v => {
    const old = get().vol
    setVol(v)
    if (v !== old && v % 10 === 0 && v !== lastTick && navigator.vibrate) { lastTick = v; navigator.vibrate(4) }
    interact()
  }
  slider.addEventListener('pointerdown', e => {
    slider.setPointerCapture(e.pointerId)
    slider.classList.add('dragging')
    set(fromPointer(e))
  })
  slider.addEventListener('pointermove', e => { if (slider.hasPointerCapture(e.pointerId)) set(fromPointer(e)) })
  const release = e => { slider.classList.remove('dragging'); if (slider.hasPointerCapture(e.pointerId)) slider.releasePointerCapture(e.pointerId) }
  slider.addEventListener('pointerup', release)
  slider.addEventListener('pointercancel', release)
  slider.addEventListener('wheel', e => { e.preventDefault(); set(get().vol + (e.deltaY < 0 ? 2 : -2)) }, { passive: false })
  slider.addEventListener('keydown', e => {
    const v = get().vol
    const step = e.shiftKey ? 10 : 2
    const map = { ArrowUp: v + step, ArrowRight: v + step, ArrowDown: v - step, ArrowLeft: v - step, PageUp: v + 10, PageDown: v - 10, Home: 0, End: 100 }
    if (e.key in map) { e.preventDefault(); set(map[e.key]) } else if (e.key === 'm' || e.key === 'M') toggleMute()
  })

  return { el: col, paint }
}

// ---------- build UI ----------
const $ = id => document.getElementById(id)
const panel = $('panel')
const widgets = []

function changed () { saveState(); audio.apply(); widgets.forEach(w => w.paint()) }

widgets.push(createVolume({
  id: 'master', name: 'Media', icon: 'media', master: true,
  get: () => ({ vol: state.master, muted: state.masterMuted }),
  setVol: v => { state.master = clamp(v); state.masterMuted = false; changed() },
  toggleMute: () => { state.masterMuted = !state.masterMuted; changed() }
}))
$('system').append(widgets[0].el)

state.apps.forEach(app => {
  const play = document.createElement('button')
  play.type = 'button'
  play.className = 'play'
  play.textContent = '▶'
  play.setAttribute('aria-label', `Play ${app.name} test sound`)
  play.setAttribute('aria-pressed', 'false')
  play.addEventListener('click', () => {
    app.playing = !app.playing
    play.setAttribute('aria-pressed', String(app.playing))
    play.textContent = app.playing ? '■' : '▶'
    play.setAttribute('aria-label', `${app.playing ? 'Stop' : 'Play'} ${app.name} test sound`)
    app.playing ? audio.start(app) : audio.stop(app)
    interact()
  })
  const w = createVolume({
    id: app.id, name: app.name, icon: app.icon, extra: play,
    get: () => app,
    setVol: v => { app.vol = clamp(v); app.muted = false; changed() },
    toggleMute: () => { app.muted = !app.muted; changed() }
  })
  widgets.push(w)
  $('apps').append(w.el)
})

// ---------- panel behaviour ----------
let hideTimer = null
let hovering = false
function interact () {
  panel.classList.remove('hidden')
  clearTimeout(hideTimer)
  if (!$('pin').checked && !hovering) hideTimer = setTimeout(() => panel.classList.add('hidden'), 3500)
}
panel.addEventListener('pointerenter', () => { hovering = true; clearTimeout(hideTimer) })
panel.addEventListener('pointerleave', () => { hovering = false; interact() })
$('pin').addEventListener('change', interact)

const appsEl = $('apps')
const edge = () => appsEl.classList.toggle('at-end', appsEl.scrollLeft + appsEl.clientWidth >= appsEl.scrollWidth - 2)
appsEl.addEventListener('scroll', () => { edge(); interact() }, { passive: true })
// a plain mouse wheel over the strip (not over a slider) scrolls it sideways
appsEl.addEventListener('wheel', e => {
  if (e.target.closest('.slider') || Math.abs(e.deltaX) > Math.abs(e.deltaY)) return
  appsEl.scrollLeft += e.deltaY
  e.preventDefault()
}, { passive: false })

$('expand').addEventListener('click', () => {
  const open = $('expand').getAttribute('aria-expanded') !== 'true'
  $('expand').setAttribute('aria-expanded', String(open))
  panel.classList.toggle('collapsed', !open)
  interact()
})

const hardwareKey = dir => { audio.ensure(); state.master = clamp(state.master + dir * 6); state.masterMuted = false; changed(); interact() }
$('vol-up').addEventListener('click', () => hardwareKey(1))
$('vol-down').addEventListener('click', () => hardwareKey(-1))

// ---------- chrome ----------
$('brand').appendChild(dotText('NTVSLIDE', { dot: 5, r: 1.8, color: '#f5f5f5' }))
const tickClock = () => { $('clock').textContent = new Date().toTimeString().slice(0, 5) }
tickClock(); setInterval(tickClock, 15000)

changed()
interact()
edge()

// exposed for tests / console tinkering
window.ntvslide = { state, audio, clamp }
