import type { Dispatch, SetStateAction } from 'react'
import type { CategoryNode, Counterparty, Profile, TransactionDetail, TransactionDraft, TransactionType } from './types'

export const transactionTypes: { value: TransactionType; label: string }[] = [
  { value: 'EXPENSE', label: 'Expense' },
  { value: 'INCOME', label: 'Income' },
  { value: 'TRANSFER', label: 'Transfer' },
  { value: 'LEND', label: 'Lend' },
  { value: 'BORROW', label: 'Borrow' },
  { value: 'REPAYMENT_IN', label: 'Repayment In' },
  { value: 'REPAYMENT_OUT', label: 'Repayment Out' },
]

export function defaultTransactionDraft(): TransactionDraft {
  const now = new Date()
  return {
    transactionType: 'EXPENSE',
    amount: '',
    date: now.toISOString().slice(0, 10),
    time: `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`,
    fromAccountId: '',
    toAccountId: '',
    rootCategoryId: '',
    categoryId: '',
    counterpartyId: '',
    note: '',
    dueDate: '',
    referenceNo: '',
    locationText: '',
    baseTxnId: '',
    shared: false,
    sharedParticipantCount: '1',
    householdId: '',
  }
}

export function resetDraftForNext(defaultAccountId: string) {
  return {
    ...defaultTransactionDraft(),
    fromAccountId: defaultAccountId,
    toAccountId: defaultAccountId,
  }
}

export function safeReadProfile() {
  try {
    const raw = localStorage.getItem('expense_profile')
    return raw ? (JSON.parse(raw) as Profile) : null
  } catch {
    return null
  }
}

export function flattenCategories(nodes: CategoryNode[]): CategoryNode[] {
  return nodes.flatMap((node) => [node, ...flattenCategories(node.children)])
}

export function matchesKind(kind: CategoryNode['categoryKind'], type: TransactionType) {
  if (type === 'INCOME') {
    return kind !== 'EXPENSE'
  }
  if (type === 'EXPENSE') {
    return kind !== 'INCOME'
  }
  return kind !== 'INCOME'
}

export function usesFromAccount(type: TransactionType) {
  return ['EXPENSE', 'TRANSFER', 'LEND', 'REPAYMENT_OUT'].includes(type)
}

export function usesToAccount(type: TransactionType) {
  return ['INCOME', 'TRANSFER', 'BORROW', 'REPAYMENT_IN'].includes(type)
}

export function transactionUsesCategory(type: TransactionType) {
  return ['EXPENSE', 'INCOME'].includes(type)
}

export function transactionNeedsCounterparty(type: TransactionType) {
  return ['EXPENSE', 'LEND', 'BORROW', 'REPAYMENT_IN', 'REPAYMENT_OUT'].includes(type)
}

export function counterpartyOptionsForType(counterparties: Counterparty[], personOnly: boolean) {
  return counterparties.filter((counterparty) =>
    counterparty.active && (!personOnly || counterparty.counterpartyType === 'PERSON' || counterparty.counterpartyType === 'BOTH'),
  )
}

export function buildTransactionPayload(draft: TransactionDraft) {
  return {
    transactionType: draft.transactionType,
    amount: Number(draft.amount),
    transactionAt: new Date(`${draft.date}T${draft.time || '00:00'}:00`).toISOString(),
    fromAccountId: draft.fromAccountId || null,
    toAccountId: draft.toAccountId || null,
    categoryId: draft.categoryId || draft.rootCategoryId || null,
    counterpartyId: draft.counterpartyId || null,
    note: draft.note || null,
    dueDate: draft.dueDate || null,
    referenceNo: draft.referenceNo || null,
    locationText: draft.locationText || null,
    baseTxnId: draft.baseTxnId || null,
    shared: draft.shared,
    sharedParticipantCount: Number(draft.sharedParticipantCount || 1),
    householdId: draft.shared ? draft.householdId || null : null,
  }
}

export function detailToDraft(detail: TransactionDetail, flattenedCategories: CategoryNode[]): TransactionDraft {
  const timestamp = new Date(detail.transactionAt)
  const category = flattenedCategories.find((item) => item.id === detail.categoryId) ?? null
  return {
    id: detail.id,
    transactionType: detail.transactionType,
    amount: String(detail.amount),
    date: timestamp.toISOString().slice(0, 10),
    time: `${String(timestamp.getHours()).padStart(2, '0')}:${String(timestamp.getMinutes()).padStart(2, '0')}`,
    fromAccountId: detail.fromAccountId ?? '',
    toAccountId: detail.toAccountId ?? '',
    rootCategoryId: category?.parentCategoryId ?? category?.id ?? '',
    categoryId: category?.parentCategoryId ? category.id : '',
    counterpartyId: detail.counterpartyId ?? '',
    note: detail.note ?? '',
    dueDate: detail.dueDate ?? '',
    referenceNo: detail.referenceNo ?? '',
    locationText: detail.locationText ?? '',
    baseTxnId: '',
    shared: detail.shared,
    sharedParticipantCount: String(detail.sharedParticipantCount ?? 1),
    householdId: detail.householdId ?? '',
  }
}

export function applyTopCategory(
  label: string,
  rootCategories: CategoryNode[],
  setDraft: Dispatch<SetStateAction<TransactionDraft>>,
) {
  const root = rootCategories.find((item) => item.name === label)
  if (!root) {
    return
  }
  setDraft((current) => ({
    ...current,
    rootCategoryId: root.id,
    categoryId: '',
  }))
}

export function formatCurrency(value: number) {
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 }).format(value || 0)
}

export function formatDateTime(value: string) {
  return new Date(value).toLocaleString('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function formatDate(value: string) {
  return new Date(value).toLocaleDateString('en-IN', { day: '2-digit', month: 'short' })
}

export function formatType(type: TransactionType) {
  return type.replaceAll('_', ' ')
}
