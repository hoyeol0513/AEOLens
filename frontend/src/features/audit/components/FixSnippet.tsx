import { useState } from 'react'
import { Icon } from '../../../shared/ui/Icon'

const snippet = `<article aria-labelledby="guide-title">\n  <h1 id="guide-title">Your guide title</h1>\n  <p>Lead with a concise, standalone answer.</p>\n</article>`

export function FixSnippet() {
  const [copied, setCopied] = useState(false)

  const copySnippet = async () => {
    await navigator.clipboard.writeText(snippet)
    setCopied(true)
    window.setTimeout(() => setCopied(false), 1800)
  }

  return (
    <section className="card snippet-card">
      <div className="snippet-copy"><span className="section-kicker"><Icon name="code" size={14} /> EXAMPLE FIX</span><h2>Make your primary answer explicit</h2><p>A semantic article wrapper and a labelled heading give crawlers and assistive technology the same reliable entry point.</p><div className="impact-row"><span><Icon name="bot" size={15} /> AEO</span><span><Icon name="activity" size={15} /> A11y</span></div></div>
      <div className="code-window"><div className="code-top"><span>React · JSX</span><button type="button" onClick={() => void copySnippet()}><Icon name={copied ? 'check' : 'copy'} size={15} />{copied ? 'Copied' : 'Copy'}</button></div><pre><code><span className="code-muted">{'<article '}</span><span className="code-key">aria-labelledby</span><span className="code-muted">={'"guide-title"'}{'>'}</span>{'\n  '}<span className="code-muted">{'<h1 '}</span><span className="code-key">id</span><span className="code-muted">={'"guide-title"'}{'>'}</span>{'\n    '}Your guide title{'\n  '}<span className="code-muted">{'</h1>'}</span>{'\n  '}<span className="code-muted">{'<p>'}</span>Lead with a concise answer.<span className="code-muted">{'</p>'}</span>{'\n'}<span className="code-muted">{'</article>'}</span></code></pre></div>
    </section>
  )
}
