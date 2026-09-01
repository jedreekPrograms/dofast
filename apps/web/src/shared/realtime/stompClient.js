function websocketUrl() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws`
}

function frame(command, headers = {}, body = '') {
  const headerLines = Object.entries(headers)
    .map(([key, value]) => `${key}:${String(value)}`)
    .join('\n')
  return `${command}\n${headerLines}\n\n${body}\0`
}

export class StompClient {
  constructor(token, onStatus = () => {}) {
    this.token = token
    this.onStatus = onStatus
    this.socket = null
    this.connected = false
    this.shouldReconnect = false
    this.reconnectAttempt = 0
    this.reconnectTimer = null
    this.reconnectImmediately = false
    this.buffer = ''
    this.subscriptionSequence = 0
    this.subscriptions = new Map()
  }

  connect() {
    if (!this.token || this.socket) return
    this.shouldReconnect = true
    this.openSocket()
  }

  updateToken(token) {
    const normalizedToken = typeof token === 'string' && token.trim() ? token : null
    if (normalizedToken === this.token) return

    this.token = normalizedToken
    if (!this.token) {
      this.disconnect()
      return
    }
    if (!this.shouldReconnect) return

    window.clearTimeout(this.reconnectTimer)
    this.reconnectTimer = null
    this.reconnectAttempt = 0
    if (!this.socket) {
      this.openSocket()
      return
    }

    this.reconnectImmediately = true
    this.connected = false
    this.socket.close()
  }

  disconnect() {
    this.shouldReconnect = false
    this.reconnectImmediately = false
    window.clearTimeout(this.reconnectTimer)
    this.reconnectTimer = null
    this.connected = false
    if (this.socket?.readyState === WebSocket.OPEN) {
      this.socket.send(frame('DISCONNECT', { receipt: 'disconnect' }))
    }
    this.socket?.close()
    this.socket = null
    this.onStatus('disconnected')
  }

  subscribe(destination, callback) {
    const id = `sub-${++this.subscriptionSequence}`
    this.subscriptions.set(id, { destination, callback })
    if (this.connected) this.sendSubscription(id, destination)

    return () => {
      const subscription = this.subscriptions.get(id)
      if (!subscription) return
      if (this.connected) {
        this.socket?.send(frame('UNSUBSCRIBE', { id }))
      }
      this.subscriptions.delete(id)
    }
  }

  send(destination, payload) {
    if (!this.connected || this.socket?.readyState !== WebSocket.OPEN) {
      return false
    }
    this.socket.send(frame('SEND', {
      destination,
      'content-type': 'application/json',
    }, JSON.stringify(payload)))
    return true
  }

  openSocket() {
    this.onStatus('connecting')
    const socket = new WebSocket(websocketUrl())
    this.socket = socket

    socket.onopen = () => {
      socket.send(frame('CONNECT', {
        'accept-version': '1.2',
        'heart-beat': '0,0',
        Authorization: `Bearer ${this.token}`,
      }))
    }

    socket.onmessage = (event) => {
      this.buffer += typeof event.data === 'string' ? event.data : ''
      this.consumeFrames()
    }

    socket.onerror = () => {
      this.onStatus('error')
    }

    socket.onclose = () => {
      const reconnectImmediately = this.reconnectImmediately
      this.reconnectImmediately = false
      this.connected = false
      this.socket = null
      this.onStatus('disconnected')
      if (!this.shouldReconnect) return
      if (reconnectImmediately) {
        this.openSocket()
      } else {
        this.scheduleReconnect()
      }
    }
  }

  consumeFrames() {
    while (this.buffer.includes('\0')) {
      const separator = this.buffer.indexOf('\0')
      const raw = this.buffer.slice(0, separator)
      this.buffer = this.buffer.slice(separator + 1)
      this.handleFrame(raw.replace(/^\n+/, ''))
    }
  }

  handleFrame(raw) {
    if (!raw.trim()) return
    const boundary = raw.indexOf('\n\n')
    const headerPart = boundary >= 0 ? raw.slice(0, boundary) : raw
    const body = boundary >= 0 ? raw.slice(boundary + 2) : ''
    const lines = headerPart.split('\n')
    const command = lines.shift()?.trim()
    const headers = Object.fromEntries(lines
      .filter((line) => line.includes(':'))
      .map((line) => {
        const index = line.indexOf(':')
        return [line.slice(0, index), line.slice(index + 1)]
      }))

    if (command === 'CONNECTED') {
      this.connected = true
      this.reconnectAttempt = 0
      this.onStatus('connected')
      for (const [id, subscription] of this.subscriptions.entries()) {
        this.sendSubscription(id, subscription.destination)
      }
      return
    }

    if (command === 'MESSAGE') {
      const subscription = this.subscriptions.get(headers.subscription)
      if (!subscription) return
      try {
        subscription.callback(JSON.parse(body))
      } catch {
        subscription.callback(body)
      }
      return
    }

    if (command === 'ERROR') {
      this.onStatus('error')
      this.socket?.close()
    }
  }

  sendSubscription(id, destination) {
    this.socket?.send(frame('SUBSCRIBE', {
      id,
      destination,
      ack: 'auto',
    }))
  }

  scheduleReconnect() {
    window.clearTimeout(this.reconnectTimer)
    const delay = Math.min(10000, 1000 * (2 ** this.reconnectAttempt))
    this.reconnectAttempt += 1
    this.reconnectTimer = window.setTimeout(() => this.openSocket(), delay)
  }
}
