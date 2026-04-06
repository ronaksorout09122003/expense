export type TransactionType =
  | 'EXPENSE'
  | 'INCOME'
  | 'TRANSFER'
  | 'LEND'
  | 'BORROW'
  | 'REPAYMENT_IN'
  | 'REPAYMENT_OUT'

export type AccountType = 'CASH' | 'BANK' | 'UPI' | 'WALLET' | 'CREDIT_CARD' | 'LOAN'

export type CounterpartyType = 'MERCHANT' | 'PERSON' | 'BOTH'

export type CategoryKind = 'EXPENSE' | 'INCOME' | 'SHARED'

export interface Profile {
  id: string
  fullName: string
  email: string
  mobile: string | null
  currencyCode: string
  timezone: string
}

export interface Settings {
  id: string
  defaultAccountId: string | null
  defaultCurrency: string
  dateFormat: string
  biometricEnabled: boolean
  reminderEnabled: boolean
  sessionTimeoutMinutes: number
}

export interface Account {
  id: string
  name: string
  accountType: AccountType
  openingBalance: number
  currentBalance: number
  accentColor: string | null
  active: boolean
}

export interface CategoryNode {
  id: string
  name: string
  categoryKind: CategoryKind
  parentCategoryId: string | null
  active: boolean
  sortOrder: number
  children: CategoryNode[]
}

export interface Counterparty {
  id: string
  name: string
  counterpartyType: CounterpartyType
  phone: string | null
  notes: string | null
  active: boolean
}

export interface SettlementInfo {
  id: string
  settlementTxnId: string
  transactionAt: string
  settledAmount: number
  note: string | null
}

export interface TransactionSummary {
  id: string
  transactionType: TransactionType
  amount: number
  transactionAt: string
  note: string | null
  status: 'ACTIVE' | 'VOIDED'
  fromAccountId: string | null
  fromAccountName: string | null
  toAccountId: string | null
  toAccountName: string | null
  categoryId: string | null
  categoryName: string | null
  categoryPath: string | null
  counterpartyId: string | null
  counterpartyName: string | null
  dueDate: string | null
  outstandingAmount: number
  shared: boolean
  sharedParticipantCount: number
  householdId: string | null
  householdName: string | null
}

export interface TransactionDetail extends TransactionSummary {
  referenceNo: string | null
  locationText: string | null
  settlements: SettlementInfo[]
}

export interface PagedResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface DashboardData {
  todayExpense: number
  yesterdayExpense: number
  currentMonthExpense: number
  cashBalance: number
  nonCashBalance: number
  receivableOutstanding: number
  payableOutstanding: number
  recentTransactions: TransactionSummary[]
  topCategories: SummaryPoint[]
  overdueCount: number
}

export interface SummaryPoint {
  label: string
  amount: number
  count: number
}

export interface TimelinePoint {
  date: string
  incoming: number
  outgoing: number
  net: number
}

export interface LedgerSummary {
  counterpartyId: string
  counterpartyName: string
  outstandingAmount: number
  totalBaseAmount: number
  openItems: number
  lastActivityAt: string | null
}

export interface CounterpartyLedger {
  counterpartyId: string
  counterpartyName: string
  receivableOutstanding: number
  payableOutstanding: number
  transactions: TransactionDetail[]
}

export interface AuthResponse {
  token: string
  profile: Profile
  message: string
}

export interface MessageResponse {
  message: string
}

export interface ReportBundle {
  categories: SummaryPoint[]
  subcategories: SummaryPoint[]
  accounts: SummaryPoint[]
  counterparties: SummaryPoint[]
  cashFlow: TimelinePoint[]
}

export interface PeriodComparison {
  currentExpense: number
  previousExpense: number
  deltaAmount: number
  deltaPercent: number
}

export interface Forecast {
  averageDailyExpense: number
  averageDailyIncome: number
  projectedMonthExpense: number
  projectedMonthIncome: number
  projectedMonthNet: number
}

export type RecurringFrequency = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'CUSTOM_DAYS'

export interface RecurringRule {
  id: string
  title: string
  transactionType: TransactionType
  amount: number
  accountId: string | null
  accountName: string | null
  toAccountId: string | null
  toAccountName: string | null
  categoryId: string | null
  categoryName: string | null
  counterpartyId: string | null
  counterpartyName: string | null
  frequencyType: RecurringFrequency
  nextRunAt: string
  intervalDays: number
  dueDateOffsetDays: number
  remindDaysBefore: number
  note: string | null
  referenceNo: string | null
  locationText: string | null
  autoCreate: boolean
  active: boolean
  shared: boolean
  sharedParticipantCount: number
  householdId: string | null
  householdName: string | null
  lastRunAt: string | null
}

export interface CalendarItem {
  kind: string
  title: string
  amount: number
  dueDate: string
  detail: string
}

export interface Budget {
  id: string
  title: string
  categoryId: string | null
  categoryName: string
  amountLimit: number
  periodStart: string
  periodEnd: string
  alertPercent: number
  rolloverEnabled: boolean
  active: boolean
  spentAmount: number
  remainingAmount: number
  usagePercent: number
}

export interface SavingsGoal {
  id: string
  title: string
  targetAmount: number
  savedAmount: number
  targetDate: string | null
  accountId: string | null
  accountName: string | null
  notes: string | null
  active: boolean
  progressPercent: number
}

export interface Reminder {
  severity: 'low' | 'medium' | 'high'
  title: string
  message: string
  dueDate: string | null
  amount: number | null
}

export interface Attachment {
  id: string
  fileUrl: string
  mimeType: string | null
  label: string | null
  createdAt: string
}

export interface SecurityOverview {
  hasPin: boolean
  biometricEnabled: boolean
  reminderEnabled: boolean
  sessionTimeoutMinutes: number
}

export interface ImportPreviewRow {
  lineNumber: number
  valid: boolean
  message: string
}

export interface ImportResult {
  importedCount: number
  preview: ImportPreviewRow[]
}

export interface AuditEntry {
  id: string
  entityName: string
  entityId: string
  actionName: string
  oldValue: string | null
  newValue: string | null
  createdAt: string
}

export interface HouseholdMember {
  userId: string
  fullName: string
  email: string
  role: 'OWNER' | 'MEMBER'
}

export interface SharedTransaction {
  transactionId: string
  ownerName: string
  transactionType: string
  amount: number
  estimatedShare: number
  note: string | null
  transactionAt: string
  participantCount: number
}

export interface Household {
  id: string
  name: string
  inviteCode: string
  members: HouseholdMember[]
  recentSharedTransactions: SharedTransaction[]
  totalSharedSpend: number
}

export interface CurrentHousehold {
  joined: boolean
  household: Household | null
}

export interface TransactionDraft {
  id?: string
  transactionType: TransactionType
  amount: string
  date: string
  time: string
  fromAccountId: string
  toAccountId: string
  rootCategoryId: string
  categoryId: string
  counterpartyId: string
  note: string
  dueDate: string
  referenceNo: string
  locationText: string
  baseTxnId: string
  shared: boolean
  sharedParticipantCount: string
  householdId: string
}
