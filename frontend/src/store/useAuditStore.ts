import { create } from 'zustand'
import { requestAudit } from '../api/auditApi'

export type Severity = 'critical' | 'warning' | 'passed'
export type Section = 'overview' | 'projects' | 'history' | 'api' | 'settings'

export interface Issue {
  id: string
  severity: Severity
  title: string
  description: string
  count: number
  selector: string
  fix: string
}

interface AuditScores { overall: number; aeo: number; a11y: number }
interface AiInsight { status: 'idle' | 'ready' | 'not-configured' | 'complete'; feedback: string }

interface AuditState {
  url: string
  urlError: string
  isAnalyzing: boolean
  progress: number
  progressLabel: string
  lastAudit: string
  scannedPages: number
  scores: AuditScores
  issues: Issue[]
  aiInsight: AiInsight
  activeSection: Section
  setUrl: (url: string) => void
  setSection: (section: Section) => void
  runAudit: () => Promise<void>
}

const initialIssues: Issue[] = [
  { id: 'schema', severity: 'critical', title: 'Article schema is missing', description: 'Guide pages have no JSON-LD to explain authorship, dates, and topic.', count: 6, selector: 'main > article.guide', fix: 'Add a valid Article JSON-LD block with headline, author, datePublished, and mainEntityOfPage.' },
  { id: 'alt', severity: 'critical', title: 'Images are missing alt text', description: 'Informative images cannot be understood by screen readers or multimodal crawlers.', count: 4, selector: 'img:not([alt])', fix: 'Add concise alt text that communicates the image purpose, or use alt="" for decorative images.' },
  { id: 'heading', severity: 'warning', title: 'Heading hierarchy skips levels', description: 'Two sections jump from H2 to H4, weakening the document outline.', count: 2, selector: 'article h2 + section > h4', fix: 'Use headings in a logical sequence. Change these H4 elements to H3 headings.' },
  { id: 'contrast', severity: 'warning', title: 'Low contrast on secondary text', description: 'Muted captions fall below the WCAG AA contrast threshold of 4.5:1.', count: 8, selector: '.text-muted, figcaption', fix: 'Darken the text color to at least #667085 on a white background to meet WCAG AA.' },
  { id: 'landmarks', severity: 'passed', title: 'Landmark structure is healthy', description: 'Main, navigation, header, and footer regions are uniquely identifiable.', count: 18, selector: 'header, nav, main, footer', fix: 'No action needed. Keep landmark labels unique as the site grows.' },
]

export const useAuditStore = create<AuditState>((set, get) => ({
  url: 'https://example.com',
  urlError: '',
  isAnalyzing: false,
  progress: 0,
  progressLabel: '',
  lastAudit: '2 minutes ago',
  scannedPages: 18,
  scores: { overall: 82, aeo: 78, a11y: 86 },
  issues: initialIssues,
  aiInsight: { status: 'idle', feedback: 'Run an audit to receive a contextual-readiness status from the backend.' },
  activeSection: 'overview',
  setUrl: (url) => set({ url, urlError: '' }),
  setSection: (activeSection) => set({ activeSection }),
  runAudit: async () => {
    const candidate = get().url.trim()
    try {
      const parsed = new URL(candidate)
      if (!['http:', 'https:'].includes(parsed.protocol)) throw new Error('Invalid protocol')
    } catch {
      set({ urlError: 'Enter a complete URL beginning with http:// or https://.' })
      return
    }

    set({ isAnalyzing: true, progress: 12, progressLabel: 'Fetching static HTML…', urlError: '' })
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
      const result = await requestAudit(candidate)
      window.clearInterval(progressTimer)
      set({ progress: 100, progressLabel: 'Report ready' })
      await new Promise((resolve) => window.setTimeout(resolve, 180))
      set({
        isAnalyzing: false,
        progress: 0,
        progressLabel: '',
        lastAudit: 'just now',
        scannedPages: result.scannedPages,
        scores: result.scores,
        issues: result.issues,
        aiInsight: { status: result.ai.status, feedback: result.ai.feedback },
      })
    } catch (error) {
      window.clearInterval(progressTimer)
      const message = error instanceof Error ? error.message : 'The audit could not be completed.'
      set({
        isAnalyzing: false,
        progress: 0,
        progressLabel: '',
        urlError: message.includes('Failed to fetch')
          ? 'AeoLens API is offline. Start the Spring Boot backend on port 8080.'
          : message,
      })
    }
  },
}))
