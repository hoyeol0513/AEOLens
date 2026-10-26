import { create } from 'zustand'
import { requestAudit } from '../api/auditApi'
import type { AuditPhase, AuditResult } from './audit.types'

interface AuditState {
  url: string
  urlError: string
  phase: AuditPhase
  progress: number
  progressLabel: string
  result: AuditResult | null
  setUrl: (url: string) => void
  runAudit: () => Promise<void>
  resetAudit: () => void
}

const initialState = {
  url: '',
  urlError: '',
  phase: 'input' as const,
  progress: 0,
  progressLabel: '',
  result: null,
}

let activeRequest: AbortController | null = null

export const useAuditStore = create<AuditState>((set, get) => ({
  ...initialState,
  setUrl: (url) => set({ url, urlError: '' }),
  resetAudit: () => {
    activeRequest?.abort()
    activeRequest = null
    set(initialState)
  },
  runAudit: async () => {
    const candidate = get().url.trim()
    try {
      const parsed = new URL(candidate)
      if (!['http:', 'https:'].includes(parsed.protocol)) throw new Error('Invalid protocol')
    } catch {
      set({ urlError: 'Enter a complete URL beginning with http:// or https://.' })
      return
    }

    activeRequest?.abort()
    const controller = new AbortController()
    activeRequest = controller
    set({ phase: 'loading', progress: 12, progressLabel: 'Fetching static HTML…', urlError: '', result: null })
    const stages = [
      { progress: 38, progressLabel: 'Mapping semantic structure…' },
      { progress: 64, progressLabel: 'Checking accessibility rules…' },
      { progress: 86, progressLabel: 'Preparing the report…' },
    ]
    let stage = 0
    const progressTimer = window.setInterval(() => {
      if (stage < stages.length) set(stages[stage++])
    }, 900)

    try {
      const result = await requestAudit(candidate, controller.signal)
      if (controller.signal.aborted) return
      set({ progress: 100, progressLabel: 'Report ready' })
      await new Promise((resolve) => window.setTimeout(resolve, 250))
      if (controller.signal.aborted) return
      set({ phase: 'result', progress: 0, progressLabel: '', result })
    } catch (error) {
      if (controller.signal.aborted) return
      const message = error instanceof Error ? error.message : 'The audit could not be completed.'
      set({
        phase: 'input',
        progress: 0,
        progressLabel: '',
        urlError: message.includes('Failed to fetch')
          ? 'AeoLens API is offline. Start the Spring Boot backend on port 8080.'
          : message,
      })
    } finally {
      window.clearInterval(progressTimer)
      if (activeRequest === controller) activeRequest = null
    }
  },
}))
