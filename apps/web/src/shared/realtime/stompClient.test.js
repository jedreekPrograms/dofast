import assert from 'node:assert/strict'
import test from 'node:test'

import { StompClient } from './stompClient.js'

class FakeWebSocket {
  static CONNECTING = 0
  static OPEN = 1
  static CLOSED = 3
  static instances = []

  constructor(url) {
    this.url = url
    this.readyState = FakeWebSocket.CONNECTING
    this.sent = []
    FakeWebSocket.instances.push(this)
  }

  open() {
    this.readyState = FakeWebSocket.OPEN
    this.onopen?.()
  }

  receive(data) {
    this.onmessage?.({ data })
  }

  send(data) {
    this.sent.push(data)
  }

  close() {
    if (this.readyState === FakeWebSocket.CLOSED) return
    this.readyState = FakeWebSocket.CLOSED
    this.onclose?.()
  }
}

test.beforeEach(() => {
  FakeWebSocket.instances = []
  globalThis.WebSocket = FakeWebSocket
  globalThis.window = {
    location: { protocol: 'https:', host: 'app.example.test' },
    clearTimeout,
    setTimeout,
  }
})

test.afterEach(() => {
  delete globalThis.WebSocket
  delete globalThis.window
})

test('reconnects with rotated token and restores subscriptions', () => {
  const client = new StompClient('first-access')
  client.connect()
  const firstSocket = FakeWebSocket.instances[0]
  firstSocket.open()
  firstSocket.receive('CONNECTED\nversion:1.2\n\n\0')
  client.subscribe('/topic/chat/42', () => {})

  assert.match(firstSocket.sent[0], /Authorization:Bearer first-access/)
  assert.ok(firstSocket.sent.some((value) => value.startsWith('SUBSCRIBE\n')))

  client.updateToken('second-access')
  const secondSocket = FakeWebSocket.instances[1]
  secondSocket.open()
  secondSocket.receive('CONNECTED\nversion:1.2\n\n\0')

  assert.match(secondSocket.sent[0], /Authorization:Bearer second-access/)
  assert.ok(secondSocket.sent.some((value) => value.startsWith('SUBSCRIBE\n')))
  client.disconnect()
})

test('clearing access token disconnects without reconnecting', () => {
  const client = new StompClient('current-access')
  client.connect()
  const socket = FakeWebSocket.instances[0]
  socket.open()

  client.updateToken(null)

  assert.equal(socket.readyState, FakeWebSocket.CLOSED)
  assert.equal(FakeWebSocket.instances.length, 1)
})
