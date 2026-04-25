import '@testing-library/jest-dom/vitest'
import 'fake-indexeddb/auto'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { server } from './server'

// jsdom が未実装のメソッドを stub 化して警告を抑制
window.scrollTo = () => {}

beforeAll(() => server.listen())
afterEach(() => {
  cleanup()
  server.resetHandlers()
})
afterAll(() => server.close())
