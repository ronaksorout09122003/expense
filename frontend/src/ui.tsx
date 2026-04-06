import type { FormEvent, ReactNode } from 'react'
import type {
  Account,
  CategoryNode,
  Counterparty,
  LedgerSummary,
  SummaryPoint,
  TimelinePoint,
  TransactionDetail,
  TransactionDraft,
  TransactionSummary,
  TransactionType,
} from './types'
import { formatCurrency, formatDate, formatDateTime, formatType, transactionTypes } from './utils'

function Field({
  label,
  helper,
  className = '',
  children,
}: {
  label: string
  helper?: string
  className?: string
  children: ReactNode
}) {
  return (
    <label className={`field ${className}`.trim()}>
      <span className="field-label">{label}</span>
      {children}
      {helper ? <small className="field-helper">{helper}</small> : null}
    </label>
  )
}

export function Panel({ title, eyebrow, children }: { title: string; eyebrow: string; children: ReactNode }) {
  return (
    <section className="panel">
      <div className="card-header">
        <p className="eyebrow">{eyebrow}</p>
        <h2>{title}</h2>
      </div>
      {children}
    </section>
  )
}

export function MetricCard({
  label,
  value,
  accent = 'brand',
  small = false,
}: {
  label: string
  value: number
  accent?: 'brand' | 'good' | 'alert' | 'warning' | 'neutral'
  small?: boolean
}) {
  return (
    <article className={`metric-card ${accent} ${small ? 'small' : ''}`}>
      <span>{label}</span>
      <strong>{formatCurrency(value)}</strong>
    </article>
  )
}

export function TransactionForm({
  draft,
  rootCategories,
  childCategories,
  accounts,
  counterparties,
  showCategory,
  showCounterparty,
  showFromAccount,
  showToAccount,
  showSettlementFields,
  onChange,
  onSubmit,
  submitting,
  compact = false,
}: {
  draft: TransactionDraft
  rootCategories: CategoryNode[]
  childCategories: CategoryNode[]
  accounts: Account[]
  counterparties: Counterparty[]
  showCategory: boolean
  showCounterparty: boolean
  showFromAccount: boolean
  showToAccount: boolean
  showSettlementFields: boolean
  onChange: (draft: TransactionDraft | ((current: TransactionDraft) => TransactionDraft)) => void
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
  submitting: boolean
  compact?: boolean
}) {
  const stampNow = () => {
    const now = new Date()
    const date = now.toISOString().slice(0, 10)
    const time = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
    onChange((current) => ({ ...current, date, time }))
  }

  return (
    <form className={`transaction-form ${compact ? 'compact' : ''}`} onSubmit={onSubmit}>
      <div className="form-helpers">
        <div className="chip-row">
          {[100, 250, 500, 1000].map((amount) => (
            <button key={amount} className="chip-button" type="button" onClick={() => onChange((current) => ({ ...current, amount: String(amount) }))}>
              Rs {amount}
            </button>
          ))}
        </div>
        <div className="inline-actions">
          <button className="ghost-button" type="button" onClick={stampNow}>
            Use current time
          </button>
          <button className="ghost-button" type="button" onClick={() => onChange((current) => ({ ...current, note: '' }))}>
            Clear note
          </button>
        </div>
      </div>

      <div className="form-grid">
        <Field label="Entry type">
          <select
            value={draft.transactionType}
            onChange={(event) =>
              onChange((current) => ({
                ...current,
                transactionType: event.target.value as TransactionType,
                rootCategoryId: '',
                categoryId: '',
                baseTxnId: event.target.value.startsWith('REPAYMENT') ? current.baseTxnId : '',
              }))
            }
          >
            {transactionTypes.map((item) => (
              <option key={item.value} value={item.value}>
                {item.label}
              </option>
            ))}
          </select>
        </Field>

        <Field label="Amount" helper="Use whole numbers or decimals for precise entries.">
          <input
            type="number"
            min="0.01"
            step="0.01"
            placeholder="0.00"
            value={draft.amount}
            onChange={(event) => onChange((current) => ({ ...current, amount: event.target.value }))}
            required
          />
        </Field>

        <Field label="Date">
          <input type="date" value={draft.date} onChange={(event) => onChange((current) => ({ ...current, date: event.target.value }))} required />
        </Field>

        <Field label="Time">
          <input type="time" value={draft.time} onChange={(event) => onChange((current) => ({ ...current, time: event.target.value }))} required />
        </Field>

        {showFromAccount ? (
          <Field label="From account">
            <select value={draft.fromAccountId} onChange={(event) => onChange((current) => ({ ...current, fromAccountId: event.target.value }))} required>
              <option value="">Choose source account</option>
              {accounts.filter((account) => account.active).map((account) => (
                <option key={account.id} value={account.id}>
                  {account.name}
                </option>
              ))}
            </select>
          </Field>
        ) : null}

        {showToAccount ? (
          <Field label="To account">
            <select value={draft.toAccountId} onChange={(event) => onChange((current) => ({ ...current, toAccountId: event.target.value }))} required>
              <option value="">Choose destination account</option>
              {accounts.filter((account) => account.active).map((account) => (
                <option key={account.id} value={account.id}>
                  {account.name}
                </option>
              ))}
            </select>
          </Field>
        ) : null}

        {showCategory ? (
          <>
            <Field label="Main category">
              <select value={draft.rootCategoryId} onChange={(event) => onChange((current) => ({ ...current, rootCategoryId: event.target.value, categoryId: '' }))} required>
                <option value="">Choose category</option>
                {rootCategories.map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.name}
                  </option>
                ))}
              </select>
            </Field>

            <Field label="Subcategory" helper="Optional, but useful for tighter reports.">
              <select value={draft.categoryId} onChange={(event) => onChange((current) => ({ ...current, categoryId: event.target.value }))}>
                <option value="">Choose subcategory</option>
                {childCategories.map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.name}
                  </option>
                ))}
              </select>
            </Field>
          </>
        ) : null}

        {showCounterparty ? (
          <Field label="Merchant or person">
            <select
              value={draft.counterpartyId}
              onChange={(event) => onChange((current) => ({ ...current, counterpartyId: event.target.value }))}
              required={showSettlementFields || draft.transactionType === 'LEND' || draft.transactionType === 'BORROW'}
            >
              <option value="">Choose who this involves</option>
              {counterparties.map((counterparty) => (
                <option key={counterparty.id} value={counterparty.id}>
                  {counterparty.name}
                </option>
              ))}
            </select>
          </Field>
        ) : null}

        {showSettlementFields ? (
          <Field label="Linked base transaction" helper="Paste or choose the transaction this repayment settles.">
            <input
              placeholder="Base transaction id"
              value={draft.baseTxnId}
              onChange={(event) => onChange((current) => ({ ...current, baseTxnId: event.target.value }))}
            />
          </Field>
        ) : null}

        {!compact ? (
          <>
            <Field label="Due date" helper="Useful for dues and expected receipts.">
              <input type="date" value={draft.dueDate} onChange={(event) => onChange((current) => ({ ...current, dueDate: event.target.value }))} />
            </Field>

            <Field label="Reference number">
              <input placeholder="UPI, invoice, or receipt reference" value={draft.referenceNo} onChange={(event) => onChange((current) => ({ ...current, referenceNo: event.target.value }))} />
            </Field>

            <Field label="Location" className="span-two">
              <input placeholder="Store, city, branch, or note about where it happened" value={draft.locationText} onChange={(event) => onChange((current) => ({ ...current, locationText: event.target.value }))} />
            </Field>
          </>
        ) : null}

        <Field label="Note" className="span-two" helper="Keep it short so future-you can scan it fast.">
          <textarea
            rows={compact ? 2 : 3}
            placeholder="Example: Groceries, cab ride, rent advance, salary credit"
            value={draft.note}
            onChange={(event) => onChange((current) => ({ ...current, note: event.target.value }))}
          />
        </Field>
      </div>

      <button className="primary-button" disabled={submitting}>
        {submitting ? 'Saving...' : draft.id ? 'Update transaction' : 'Save transaction'}
      </button>
    </form>
  )
}

export function TransactionTable({
  rows,
  onEdit,
  showSettlements = false,
}: {
  rows: (TransactionSummary | TransactionDetail)[]
  onEdit: (id: string) => void
  showSettlements?: boolean
}) {
  if (!rows.length) {
    return <EmptyState title="No transactions found" description="Try changing filters or add your first entry." />
  }

  return (
    <div className="table-shell">
      {rows.map((row) => (
        <div key={row.id} className="table-row">
          <div className="table-main">
            <div className="table-title-row">
              <strong>{row.categoryPath || row.counterpartyName || formatType(row.transactionType)}</strong>
              <span className={`type-pill type-${row.transactionType.toLowerCase()}`}>{formatType(row.transactionType)}</span>
            </div>
            <p>{formatDateTime(row.transactionAt)}</p>
            <p className="row-note">{row.note || 'No note saved for this entry.'}</p>
            <div className="meta-row">
              {row.fromAccountName ? <span>From: {row.fromAccountName}</span> : null}
              {row.toAccountName ? <span>To: {row.toAccountName}</span> : null}
              {row.counterpartyName ? <span>With: {row.counterpartyName}</span> : null}
              {row.dueDate ? <span>Due: {formatDate(row.dueDate)}</span> : null}
              {row.shared ? <span>Shared: {row.householdName || `${row.sharedParticipantCount} people`}</span> : null}
            </div>
            {showSettlements && 'settlements' in row && row.settlements.length ? (
              <p className="muted">Settlements: {row.settlements.map((item) => formatCurrency(item.settledAmount)).join(', ')}</p>
            ) : null}
          </div>
          <div className="row-actions">
            {row.outstandingAmount > 0 ? <span className="pill">{formatCurrency(row.outstandingAmount)} open</span> : <span className="pill neutral">Closed</span>}
            <strong>{formatCurrency(row.amount)}</strong>
            <button className="ghost-button" type="button" onClick={() => onEdit(row.id)}>
              Review
            </button>
          </div>
        </div>
      ))}
    </div>
  )
}

export function SimpleList({
  title,
  items,
  onOpen,
  activeId,
}: {
  title: string
  items: LedgerSummary[]
  onOpen: (id: string) => void
  activeId?: string
}) {
  return (
    <div className="mini-panel">
      <div className="list-header">
        <h3>{title}</h3>
        <span className="muted">{items.length} people</span>
      </div>
      {items.length ? (
        items.map((item) => (
          <button
            key={item.counterpartyId}
            type="button"
            className={`mini-row ${activeId === item.counterpartyId ? 'active' : ''}`}
            onClick={() => onOpen(item.counterpartyId)}
          >
            <div>
              <strong>{item.counterpartyName}</strong>
              <p>{item.openItems} open item(s)</p>
              {item.lastActivityAt ? <p className="muted">Last activity {formatDateTime(item.lastActivityAt)}</p> : null}
            </div>
            <span>{formatCurrency(item.outstandingAmount)}</span>
          </button>
        ))
      ) : (
        <p className="muted">No outstanding items.</p>
      )}
    </div>
  )
}

export function SummaryGrid({ items }: { items: SummaryPoint[] }) {
  if (!items.length) {
    return <EmptyState title="No report data" description="Add entries or widen the selected date range." />
  }
  return (
    <div className="table-shell">
      {items.map((item, index) => (
        <div key={item.label} className="table-row">
          <div className="table-main">
            <div className="table-title-row">
              <strong>{item.label}</strong>
              <span className="pill neutral">#{index + 1}</span>
            </div>
            <p>{item.count} linked entries</p>
          </div>
          <strong>{formatCurrency(item.amount)}</strong>
        </div>
      ))}
    </div>
  )
}

export function TimelineGrid({ items }: { items: TimelinePoint[] }) {
  if (!items.length) {
    return <EmptyState title="No cash-flow trend yet" description="Once entries exist in the selected range, the timeline will appear here." />
  }
  return (
    <div className="timeline-grid">
      {items.map((item) => (
        <div key={item.date} className="timeline-card">
          <strong>{formatDate(item.date)}</strong>
          <p>In: {formatCurrency(item.incoming)}</p>
          <p>Out: {formatCurrency(item.outgoing)}</p>
          <span className={item.net >= 0 ? 'good' : 'alert'}>Net {formatCurrency(item.net)}</span>
        </div>
      ))}
    </div>
  )
}

export function EmptyState({ title, description }: { title: string; description: string }) {
  return (
    <div className="empty-state">
      <h3>{title}</h3>
      <p>{description}</p>
    </div>
  )
}

export function FeatureBadge({ label }: { label: string }) {
  return <span className="feature-badge">{label}</span>
}
