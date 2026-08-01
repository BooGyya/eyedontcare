import { afterEach, describe, expect, it } from 'vitest'
import type { UserConfig } from 'vite'
import viteConfig from './vite.config'

const originalProxyTarget = process.env.VITE_PROXY_TARGET

afterEach(() => {
  if (originalProxyTarget === undefined) {
    delete process.env.VITE_PROXY_TARGET
    return
  }

  process.env.VITE_PROXY_TARGET = originalProxyTarget
})

describe('Vite development proxy', () => {
  it('routes API and WebSocket traffic to the configured backend target', () => {
    process.env.VITE_PROXY_TARGET = 'http://backend:8080'

    expect(viteConfig).toBeTypeOf('function')

    const createConfig = viteConfig as (environment: {
      command: 'serve'
      mode: string
    }) => UserConfig
    const config = createConfig({
      command: 'serve',
      mode: 'test',
    })
    const proxy = config.server?.proxy

    expect(proxy?.['/api']).toMatchObject({
      target: 'http://backend:8080',
      changeOrigin: true,
    })
    expect(proxy?.['/ws']).toMatchObject({
      target: 'http://backend:8080',
      ws: true,
    })
    expect(proxy?.['/ws']).not.toHaveProperty('rewriteWsOrigin')
  })
})
