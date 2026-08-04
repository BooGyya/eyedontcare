import { describe, expect, it } from 'vitest'
import { isValidPassword } from './password'

describe('isValidPassword', () => {
  it('accepts a password with letters and digits within 8~16 chars', () => {
    expect(isValidPassword('abcd1234')).toBe(true)
  })

  it('accepts a 16-char password mixing letters and digits', () => {
    expect(isValidPassword('abcdefgh12345678')).toBe(true)
  })

  it('rejects a password shorter than 8 chars', () => {
    expect(isValidPassword('abc1234')).toBe(false)
  })

  it('rejects a password longer than 16 chars', () => {
    expect(isValidPassword('abcdefgh123456789')).toBe(false)
  })

  it('rejects a letters-only password', () => {
    expect(isValidPassword('abcdefgh')).toBe(false)
  })

  it('rejects a digits-only password', () => {
    expect(isValidPassword('12345678')).toBe(false)
  })

  it('rejects an empty string', () => {
    expect(isValidPassword('')).toBe(false)
  })

  it('rejects a password with internal whitespace', () => {
    expect(isValidPassword('abcd 1234')).toBe(false)
  })

  it('rejects a password with leading or trailing whitespace', () => {
    expect(isValidPassword(' abcd1234')).toBe(false)
    expect(isValidPassword('abcd1234 ')).toBe(false)
  })

  it('rejects a password with tab whitespace', () => {
    expect(isValidPassword('abcd\t1234')).toBe(false)
  })
})
