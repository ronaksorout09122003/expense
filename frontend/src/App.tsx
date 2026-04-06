import {
  startTransition,
  useDeferredValue,
  useEffect,
  useEffectEvent,
  useMemo,
  useState,
} from 'react'
import type { FormEvent } from 'react'
import './App.css'
import { apiRequest, apiTextRequest, createQuery } from './api'
import type {
  Account,
  Attachment,
  AuditEntry,
  AuthResponse,
  Budget,
  CalendarItem,
  CategoryNode,
  Counterparty,
  CounterpartyLedger,
  CurrentHousehold,
  DashboardData,
  Forecast,
  LedgerSummary,
  MessageResponse,
  PagedResponse,
  PeriodComparison,
  Profile,
  RecurringRule,
  ReportBundle,
  Reminder,
  SecurityOverview,
  SavingsGoal,
  Settings,
  SummaryPoint,
  TimelinePoint,
  TransactionDetail,
  TransactionDraft,
  TransactionSummary,
  TransactionType,
} from './types'
import {
  applyTopCategory,
  buildTransactionPayload,
  counterpartyOptionsForType,
  defaultTransactionDraft,
  detailToDraft,
  flattenCategories,
  formatCurrency,
  matchesKind,
  resetDraftForNext,
  safeReadProfile,
  transactionNeedsCounterparty,
  transactionUsesCategory,
  usesFromAccount,
  usesToAccount,
} from './utils'
import {
  EmptyState,
  FeatureBadge,
  MetricCard,
  Panel,
  SimpleList,
  SummaryGrid,
  TimelineGrid,
  TransactionForm,
  TransactionTable,
} from './ui'

type View = 'dashboard' | 'transactions' | 'planner' | 'ledger' | 'masters' | 'reports' | 'household' | 'history' | 'settings'
type MasterTab = 'accounts' | 'categories' | 'counterparties'
type BannerTone = 'success' | 'error' | 'info'
type Banner = { tone: BannerTone; text: string } | null
type TransactionFilters = {
  page: number
  size: number
  from: string
  to: string
  transactionType: TransactionType | 'ALL'
  accountId: string
  categoryId: string
  counterpartyId: string
  query: string
  dueOnly: boolean
}

const defaultFilters = (): TransactionFilters => ({
  page: 0,
  size: 20,
  from: '',
  to: '',
  transactionType: 'ALL',
  accountId: '',
  categoryId: '',
  counterpartyId: '',
  query: '',
  dueOnly: false,
})

const navigationItems: { view: View; label: string; description: string }[] = [
  { view: 'dashboard', label: 'Dashboard', description: 'Today, balances, and quick add' },
  { view: 'transactions', label: 'Transactions', description: 'Capture, search, and update entries' },
  { view: 'planner', label: 'Planner', description: 'Recurring, budgets, goals, and alerts' },
  { view: 'ledger', label: 'Ledger', description: 'Track dues and settlements' },
  { view: 'masters', label: 'Masters', description: 'Accounts, categories, and people' },
  { view: 'reports', label: 'Reports', description: 'See patterns and trends' },
  { view: 'household', label: 'Household', description: 'Shared finance and split visibility' },
  { view: 'history', label: 'History', description: 'Audit trail and activity' },
  { view: 'settings', label: 'Settings', description: 'Tune defaults and habits' },
]

const viewPathSegments: Record<View, string | null> = {
  dashboard: null,
  transactions: 'transactions',
  planner: 'planner',
  ledger: 'ledger',
  masters: 'masters',
  reports: 'reports',
  household: 'household',
  history: 'history',
  settings: 'settings',
}

const routeViews = Object.entries(viewPathSegments).reduce<Record<string, View>>((accumulator, [view, segment]) => {
  if (segment) {
    accumulator[segment] = view as View
  }
  return accumulator
}, {})

const quickEntryPresets: { type: TransactionType; label: string; hint: string }[] = [
  { type: 'EXPENSE', label: 'Log expense', hint: 'Everyday spends in two taps' },
  { type: 'INCOME', label: 'Add income', hint: 'Salary, refunds, cash in' },
  { type: 'TRANSFER', label: 'Move money', hint: 'Shift between accounts' },
  { type: 'LEND', label: 'Record lend', hint: 'Track what others owe you' },
  { type: 'BORROW', label: 'Record borrow', hint: 'Track what you owe' },
]

const viewContent: Record<View, { eyebrow: string; title: string; description: string }> = {
  dashboard: {
    eyebrow: 'Daily finance workspace',
    title: 'Keep the day organized and the money visible',
    description: 'Capture routine entries quickly, spot overdue items early, and keep balances readable at a glance.',
  },
  transactions: {
    eyebrow: 'Capture and control',
    title: 'Enter fast, search fast, and correct fast',
    description: 'This screen is tuned for repeat daily entry, review, and cleanup without switching context.',
  },
  planner: {
    eyebrow: 'Financial planning',
    title: 'Put recurring money work on rails',
    description: 'Organize recurring entries, watch budget limits, keep goals visible, and act on reminders before they become problems.',
  },
  ledger: {
    eyebrow: 'Due follow-up',
    title: 'See who owes what and settle without confusion',
    description: 'Open any person ledger, identify outstanding items, and record settlements from the same workspace.',
  },
  masters: {
    eyebrow: 'Reusable building blocks',
    title: 'Keep accounts, categories, and people clean',
    description: 'Well-maintained masters make daily entry faster and your reports far more trustworthy.',
  },
  reports: {
    eyebrow: 'Pattern recognition',
    title: 'Understand where money moves and why',
    description: 'Switch ranges quickly to identify categories, accounts, and counterparties driving your numbers.',
  },
  household: {
    eyebrow: 'Shared money',
    title: 'Track household spending without losing ownership',
    description: 'Create a household, invite members, and keep shared transactions visible alongside your personal ledger.',
  },
  history: {
    eyebrow: 'Recorded changes',
    title: 'Review what changed and when',
    description: 'Audit logs make it easier to trust the data, understand edits, and debug mistakes quickly.',
  },
  settings: {
    eyebrow: 'Personal defaults',
    title: 'Shape the app around how you actually work',
    description: 'Choose sensible defaults so routine entries take less thought every day.',
  },
}

type RecurringDraft = {
  id?: string
  title: string
  transactionType: TransactionType
  amount: string
  accountId: string
  toAccountId: string
  categoryId: string
  counterpartyId: string
  frequencyType: 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'CUSTOM_DAYS'
  nextRunAt: string
  intervalDays: string
  dueDateOffsetDays: string
  remindDaysBefore: string
  note: string
  referenceNo: string
  locationText: string
  autoCreate: boolean
  active: boolean
  shared: boolean
  sharedParticipantCount: string
  householdId: string
}

type BudgetDraft = {
  id?: string
  title: string
  categoryId: string
  amountLimit: string
  periodStart: string
  periodEnd: string
  alertPercent: string
  rolloverEnabled: boolean
  active: boolean
}

type GoalDraft = {
  id?: string
  title: string
  targetAmount: string
  savedAmount: string
  targetDate: string
  accountId: string
  notes: string
  active: boolean
}

function toDateTimeLocalValue(value: string | null) {
  if (!value) {
    return ''
  }
  const date = new Date(value)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day}T${hours}:${minutes}`
}

function toIsoFromLocalDateTime(value: string) {
  return value ? new Date(value).toISOString() : new Date().toISOString()
}

function truncateText(value: string | null, size = 140) {
  if (!value) {
    return 'No details'
  }
  return value.length > size ? `${value.slice(0, size)}...` : value
}

function isMasterTab(value: string | null): value is MasterTab {
  return value === 'accounts' || value === 'categories' || value === 'counterparties'
}

function getAppBasePath(pathname: string) {
  const normalizedPath = pathname.replace(/\/+$/, '')
  if (!normalizedPath) {
    return ''
  }

  const segments = normalizedPath.split('/').filter(Boolean)
  if (segments.length === 0) {
    return ''
  }

  const lastSegment = segments[segments.length - 1]
  const baseSegments = routeViews[lastSegment] ? segments.slice(0, -1) : segments
  return baseSegments.length ? `/${baseSegments.join('/')}` : ''
}

function parseRoute(pathname: string, search: string) {
  const normalizedPath = pathname.replace(/\/+$/, '')
  const segments = normalizedPath.split('/').filter(Boolean)
  const lastSegment = segments[segments.length - 1] ?? ''
  const view = routeViews[lastSegment] ?? 'dashboard'
  const tab = new URLSearchParams(search).get('tab')

  return {
    view,
    masterTab: view === 'masters' && isMasterTab(tab) ? tab : 'accounts',
  }
}

function buildRoutePath(basePath: string, view: View, masterTab: MasterTab) {
  const normalizedBasePath = basePath.replace(/\/+$/, '')
  const segment = viewPathSegments[view]
  const pathname = segment ? `${normalizedBasePath}/${segment}` : `${normalizedBasePath || ''}/`

  if (view !== 'masters' || masterTab === 'accounts') {
    return pathname
  }

  const query = new URLSearchParams({ tab: masterTab }).toString()
  return `${pathname}?${query}`
}

function getHistoryIndex() {
  const state = window.history.state as { expenseHistoryIndex?: number } | null
  return typeof state?.expenseHistoryIndex === 'number' ? state.expenseHistoryIndex : 0
}

function toInputDate(value: Date) {
  return value.toISOString().slice(0, 10)
}

function getRangePreset(preset: 'today' | 'week' | 'month' | 'quarter' | 'all') {
  const now = new Date()
  if (preset === 'all') {
    return { from: '', to: '' }
  }
  if (preset === 'today') {
    const today = toInputDate(now)
    return { from: today, to: today }
  }
  if (preset === 'week') {
    return { from: toInputDate(new Date(now.getFullYear(), now.getMonth(), now.getDate() - 6)), to: toInputDate(now) }
  }
  if (preset === 'quarter') {
    return { from: toInputDate(new Date(now.getFullYear(), now.getMonth(), now.getDate() - 89)), to: toInputDate(now) }
  }
  return { from: toInputDate(new Date(now.getFullYear(), now.getMonth(), 1)), to: toInputDate(now) }
}

function buildCategoryFilterOptions(nodes: CategoryNode[], prefix = ''): { id: string; label: string }[] {
  return nodes.flatMap((node) => {
    if (!node.active) {
      return []
    }
    const label = prefix ? `${prefix} / ${node.name}` : node.name
    return [{ id: node.id, label }, ...buildCategoryFilterOptions(node.children, label)]
  })
}

function App() {
  const initialRoute = parseRoute(window.location.pathname, window.location.search)
  const appBasePath = useMemo(() => getAppBasePath(window.location.pathname), [])
  const [token, setToken] = useState<string | null>(localStorage.getItem('expense_token'))
  const [profile, setProfile] = useState<Profile | null>(safeReadProfile())
  const [authMode, setAuthMode] = useState<'login' | 'register'>('login')
  const [authDraft, setAuthDraft] = useState({
    fullName: '',
    email: 'demo@ledgerlocal.app',
    mobile: '',
    password: 'demo1234',
  })
  const [banner, setBanner] = useState<Banner>(null)
  const [activeView, setActiveView] = useState<View>(initialRoute.view)
  const [masterTab, setMasterTab] = useState<MasterTab>(initialRoute.masterTab)
  const [historyIndex, setHistoryIndex] = useState(getHistoryIndex)
  const [isMobileNavOpen, setIsMobileNavOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [bootstrapping, setBootstrapping] = useState(false)

  const [dashboard, setDashboard] = useState<DashboardData | null>(null)
  const [settings, setSettings] = useState<Settings | null>(null)
  const [accounts, setAccounts] = useState<Account[]>([])
  const [categories, setCategories] = useState<CategoryNode[]>([])
  const [counterparties, setCounterparties] = useState<Counterparty[]>([])

  const [transactionDraft, setTransactionDraft] = useState<TransactionDraft>(defaultTransactionDraft)
  const [selectedTransaction, setSelectedTransaction] = useState<TransactionDetail | null>(null)
  const [transactionsPage, setTransactionsPage] = useState<PagedResponse<TransactionSummary> | null>(null)
  const [transactionFilters, setTransactionFilters] = useState<TransactionFilters>(defaultFilters)
  const deferredQuery = useDeferredValue(transactionFilters.query)

  const [receivable, setReceivable] = useState<LedgerSummary[]>([])
  const [payable, setPayable] = useState<LedgerSummary[]>([])
  const [selectedLedgerId, setSelectedLedgerId] = useState('')
  const [selectedLedger, setSelectedLedger] = useState<CounterpartyLedger | null>(null)
  const [settlementDraft, setSettlementDraft] = useState({ amount: '', accountId: '', note: '' })

  const [reports, setReports] = useState<ReportBundle>({
    categories: [],
    subcategories: [],
    accounts: [],
    counterparties: [],
    cashFlow: [],
  })
  const [comparison, setComparison] = useState<PeriodComparison | null>(null)
  const [forecast, setForecast] = useState<Forecast | null>(null)
  const [reportRange, setReportRange] = useState({
    from: new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString().slice(0, 10),
    to: new Date().toISOString().slice(0, 10),
  })

  const [recurringRules, setRecurringRules] = useState<RecurringRule[]>([])
  const [calendarItems, setCalendarItems] = useState<CalendarItem[]>([])
  const [budgets, setBudgets] = useState<Budget[]>([])
  const [goals, setGoals] = useState<SavingsGoal[]>([])
  const [reminders, setReminders] = useState<Reminder[]>([])
  const [currentHousehold, setCurrentHousehold] = useState<CurrentHousehold | null>(null)
  const [auditEntries, setAuditEntries] = useState<AuditEntry[]>([])
  const [securityOverview, setSecurityOverview] = useState<SecurityOverview | null>(null)
  const [attachments, setAttachments] = useState<Attachment[]>([])
  const [exportedCsv, setExportedCsv] = useState('')
  const [importCsv, setImportCsv] = useState('')
  const [importResult, setImportResult] = useState<{ importedCount: number; preview: { lineNumber: number; valid: boolean; message: string }[] } | null>(null)
  const [pinDraft, setPinDraft] = useState('')
  const [unlockPin, setUnlockPin] = useState('')
  const [isLocked, setIsLocked] = useState(false)
  const [attachmentDraft, setAttachmentDraft] = useState({ fileUrl: '', mimeType: '', label: '' })
  const [householdCreateName, setHouseholdCreateName] = useState('')
  const [householdJoinCode, setHouseholdJoinCode] = useState('')
  const [recurringDraft, setRecurringDraft] = useState<RecurringDraft>({
    title: '',
    transactionType: 'EXPENSE',
    amount: '',
    accountId: '',
    toAccountId: '',
    categoryId: '',
    counterpartyId: '',
    frequencyType: 'MONTHLY',
    nextRunAt: toDateTimeLocalValue(new Date().toISOString()),
    intervalDays: '30',
    dueDateOffsetDays: '0',
    remindDaysBefore: '3',
    note: '',
    referenceNo: '',
    locationText: '',
    autoCreate: false,
    active: true,
    shared: false,
    sharedParticipantCount: '1',
    householdId: '',
  })
  const [budgetDraft, setBudgetDraft] = useState<BudgetDraft>({
    title: '',
    categoryId: '',
    amountLimit: '',
    periodStart: new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString().slice(0, 10),
    periodEnd: new Date().toISOString().slice(0, 10),
    alertPercent: '80',
    rolloverEnabled: false,
    active: true,
  })
  const [goalDraft, setGoalDraft] = useState<GoalDraft>({
    title: '',
    targetAmount: '',
    savedAmount: '0',
    targetDate: '',
    accountId: '',
    notes: '',
    active: true,
  })

  const [accountDraft, setAccountDraft] = useState({
    name: '',
    accountType: 'CASH',
    openingBalance: '0',
    accentColor: '#155eef',
    active: true,
  })
  const [categoryDraft, setCategoryDraft] = useState({
    name: '',
    parentCategoryId: '',
    categoryKind: 'EXPENSE',
    sortOrder: '0',
    active: true,
  })
  const [counterpartyDraft, setCounterpartyDraft] = useState({
    name: '',
    counterpartyType: 'MERCHANT',
    phone: '',
    notes: '',
    active: true,
  })

  const flatCategories = useMemo(() => flattenCategories(categories), [categories])
  const rootCategories = useMemo(() => categories, [categories])
  const activeRootCategories = useMemo(() => categories.filter((category) => category.active), [categories])
  const filteredRoots = useMemo(
    () => activeRootCategories.filter((category) => matchesKind(category.categoryKind, transactionDraft.transactionType)),
    [activeRootCategories, transactionDraft.transactionType],
  )
  const activeRoot = activeRootCategories.find((category) => category.id === transactionDraft.rootCategoryId) ?? null
  const childCategories = activeRoot?.children.filter((category) => category.active) ?? []
  const personCounterparties = counterpartyOptionsForType(counterparties, true)
  const availableCounterparties =
    showCounterparty(transactionDraft.transactionType) && transactionDraft.transactionType !== 'EXPENSE'
      ? personCounterparties
      : counterparties.filter((item) => item.active)
  const categoryFilterOptions = useMemo(() => buildCategoryFilterOptions(rootCategories), [rootCategories])
  const activeAccounts = useMemo(() => accounts.filter((account) => account.active), [accounts])
  const activeCounterparties = useMemo(() => counterparties.filter((item) => item.active), [counterparties])
  const householdOptions = currentHousehold?.joined && currentHousehold.household ? [currentHousehold.household] : []

  useEffect(() => {
    if (token) localStorage.setItem('expense_token', token)
    else localStorage.removeItem('expense_token')
  }, [token])

  useEffect(() => {
    if (profile) localStorage.setItem('expense_profile', JSON.stringify(profile))
    else localStorage.removeItem('expense_profile')
  }, [profile])

  useEffect(() => {
    if (!banner) return
    const timeout = window.setTimeout(() => setBanner(null), 4000)
    return () => window.clearTimeout(timeout)
  }, [banner])

  useEffect(() => {
    if (!isMobileNavOpen) {
      document.body.classList.remove('mobile-nav-open')
      return
    }

    document.body.classList.add('mobile-nav-open')

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setIsMobileNavOpen(false)
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => {
      document.body.classList.remove('mobile-nav-open')
      window.removeEventListener('keydown', handleKeyDown)
    }
  }, [isMobileNavOpen])

  useEffect(() => {
    const handleResize = () => {
      if (window.innerWidth > 960) {
        setIsMobileNavOpen(false)
      }
    }

    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  useEffect(() => {
    const canonicalPath = buildRoutePath(appBasePath, activeView, masterTab)
    const currentLocation = `${window.location.pathname}${window.location.search}`
    const currentState = window.history.state as { expenseHistoryIndex?: number } | null

    if (currentLocation !== canonicalPath || typeof currentState?.expenseHistoryIndex !== 'number') {
      window.history.replaceState(
        { ...(currentState ?? {}), expenseHistoryIndex: getHistoryIndex() },
        '',
        canonicalPath,
      )
    }
  }, [activeView, appBasePath, masterTab])

  const syncRouteFromLocation = useEffectEvent(() => {
    const route = parseRoute(window.location.pathname, window.location.search)
    setIsMobileNavOpen(false)
    setHistoryIndex(getHistoryIndex())
    if (route.view === 'masters') {
      setMasterTab(route.masterTab)
    }
    startTransition(() => setActiveView(route.view))
  })

  useEffect(() => {
    const handlePopState = () => syncRouteFromLocation()
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])

  const notify = (tone: BannerTone, text: string) => setBanner({ tone, text })
  const closeMobileNav = () => setIsMobileNavOpen(false)
  const toggleMobileNav = () => setIsMobileNavOpen((current) => !current)

  const replaceHistoryWithDashboard = () => {
    closeMobileNav()
    setHistoryIndex(0)
    window.history.replaceState(
      { ...(window.history.state ?? {}), expenseHistoryIndex: 0 },
      '',
      buildRoutePath(appBasePath, 'dashboard', masterTab),
    )
    startTransition(() => setActiveView('dashboard'))
  }

  const runLoadCore = async () => {
    if (!token) return
    setBootstrapping(true)
    try {
      const [me, loadedSettings, loadedAccounts, loadedCategories, loadedCounterparties, loadedDashboard, loadedSecurity] =
        await Promise.all([
          apiRequest<Profile>('/api/v1/me', { token }),
          apiRequest<Settings>('/api/v1/settings', { token }),
          apiRequest<Account[]>('/api/v1/accounts', { token }),
          apiRequest<CategoryNode[]>('/api/v1/categories/tree', { token }),
          apiRequest<Counterparty[]>('/api/v1/counterparties', { token }),
          apiRequest<DashboardData>('/api/v1/reports/dashboard', { token }),
          apiRequest<SecurityOverview>('/api/v1/security', { token }),
        ])
      setProfile(me)
      setSettings(loadedSettings)
      setAccounts(loadedAccounts)
      setCategories(loadedCategories)
      setCounterparties(loadedCounterparties)
      setDashboard(loadedDashboard)
      setSecurityOverview(loadedSecurity)
      setTransactionDraft((current) =>
        current.fromAccountId || current.toAccountId
          ? current
          : {
              ...current,
              fromAccountId: loadedSettings.defaultAccountId ?? loadedAccounts[0]?.id ?? '',
              toAccountId: loadedSettings.defaultAccountId ?? loadedAccounts[0]?.id ?? '',
              householdId: current.householdId || householdOptions[0]?.id || '',
            },
      )
      setSettlementDraft((current) => ({
        ...current,
        accountId: current.accountId || loadedSettings.defaultAccountId || loadedAccounts[0]?.id || '',
      }))
    } catch (error) {
      handleApiError(error, true)
    } finally {
      setBootstrapping(false)
    }
  }
  const loadCore = useEffectEvent(runLoadCore)

  const runLoadTransactions = async () => {
    if (!token) return
    try {
      const page = await apiRequest<PagedResponse<TransactionSummary>>(
        `/api/v1/transactions${createQuery({
          page: transactionFilters.page,
          size: transactionFilters.size,
          from: transactionFilters.from || undefined,
          to: transactionFilters.to || undefined,
          transactionType: transactionFilters.transactionType === 'ALL' ? undefined : transactionFilters.transactionType,
          accountId: transactionFilters.accountId || undefined,
          categoryId: transactionFilters.categoryId || undefined,
          counterpartyId: transactionFilters.counterpartyId || undefined,
          query: deferredQuery || undefined,
          dueOnly: transactionFilters.dueOnly,
        })}`,
        { token },
      )
      setTransactionsPage(page)
    } catch (error) {
      handleApiError(error)
    }
  }
  const loadTransactions = useEffectEvent(runLoadTransactions)

  const runLoadLedger = async () => {
    if (!token) return
    try {
      const [receiveList, payList] = await Promise.all([
        apiRequest<LedgerSummary[]>('/api/v1/dues/receivable', { token }),
        apiRequest<LedgerSummary[]>('/api/v1/dues/payable', { token }),
      ])
      setReceivable(receiveList)
      setPayable(payList)
      const nextId = selectedLedgerId || receiveList[0]?.counterpartyId || payList[0]?.counterpartyId || ''
      setSelectedLedgerId(nextId)
    } catch (error) {
      handleApiError(error)
    }
  }
  const loadLedger = useEffectEvent(runLoadLedger)

  const runLoadSelectedLedger = async () => {
    if (!token || !selectedLedgerId) return
    try {
      setSelectedLedger(await apiRequest<CounterpartyLedger>(`/api/v1/ledger/counterparty/${selectedLedgerId}`, { token }))
    } catch (error) {
      handleApiError(error)
    }
  }
  const loadSelectedLedger = useEffectEvent(runLoadSelectedLedger)

  const runLoadReports = async () => {
    if (!token) return
    try {
      const query = createQuery({ from: reportRange.from || undefined, to: reportRange.to || undefined })
      const [categoriesReport, subcategoriesReport, accountReport, counterpartyReport, cashFlowReport, comparisonReport, forecastReport] =
        await Promise.all([
          apiRequest<SummaryPoint[]>(`/api/v1/reports/category-summary${query}`, { token }),
          apiRequest<SummaryPoint[]>(`/api/v1/reports/subcategory-summary${query}`, { token }),
          apiRequest<SummaryPoint[]>(`/api/v1/reports/account-summary${query}`, { token }),
          apiRequest<SummaryPoint[]>(`/api/v1/reports/counterparty-summary${query}`, { token }),
          apiRequest<TimelinePoint[]>(`/api/v1/reports/cash-flow${query}`, { token }),
          apiRequest<PeriodComparison>(`/api/v1/reports/comparison${query}`, { token }),
          apiRequest<Forecast>(`/api/v1/reports/forecast${query}`, { token }),
        ])
      setReports({
        categories: categoriesReport,
        subcategories: subcategoriesReport,
        accounts: accountReport,
        counterparties: counterpartyReport,
        cashFlow: cashFlowReport,
      })
      setComparison(comparisonReport)
      setForecast(forecastReport)
    } catch (error) {
      handleApiError(error)
    }
  }
  const loadReports = useEffectEvent(runLoadReports)

  const runLoadPlanner = async () => {
    if (!token) return
    try {
      const [loadedRecurring, loadedCalendar, loadedBudgets, loadedGoals, loadedReminders] = await Promise.all([
        apiRequest<RecurringRule[]>('/api/v1/planner/recurring', { token }),
        apiRequest<CalendarItem[]>('/api/v1/planner/calendar', { token }),
        apiRequest<Budget[]>('/api/v1/planner/budgets', { token }),
        apiRequest<SavingsGoal[]>('/api/v1/planner/goals', { token }),
        apiRequest<Reminder[]>('/api/v1/planner/reminders', { token }),
      ])
      setRecurringRules(loadedRecurring)
      setCalendarItems(loadedCalendar)
      setBudgets(loadedBudgets)
      setGoals(loadedGoals)
      setReminders(loadedReminders)
    } catch (error) {
      handleApiError(error)
    }
  }
  const loadPlanner = useEffectEvent(runLoadPlanner)

  const runLoadHousehold = async () => {
    if (!token) return
    try {
      setCurrentHousehold(await apiRequest<CurrentHousehold>('/api/v1/household', { token }))
    } catch (error) {
      handleApiError(error)
    }
  }
  const loadHousehold = useEffectEvent(runLoadHousehold)

  const runLoadHistory = async () => {
    if (!token) return
    try {
      setAuditEntries(await apiRequest<AuditEntry[]>('/api/v1/audit', { token }))
    } catch (error) {
      handleApiError(error)
    }
  }
  const loadHistory = useEffectEvent(runLoadHistory)

  const runLoadAttachments = async () => {
    if (!token || !selectedTransaction) {
      setAttachments([])
      return
    }
    try {
      setAttachments(await apiRequest<Attachment[]>(`/api/v1/transactions/${selectedTransaction.id}/attachments`, { token }))
    } catch (error) {
      handleApiError(error)
    }
  }
  const loadAttachments = useEffectEvent(runLoadAttachments)

  useEffect(() => {
    if (token) void loadCore()
  }, [token])

  useEffect(() => {
    if (token) void loadTransactions()
  }, [
    token,
    transactionFilters.page,
    transactionFilters.size,
    transactionFilters.from,
    transactionFilters.to,
    transactionFilters.transactionType,
    transactionFilters.accountId,
    transactionFilters.categoryId,
    transactionFilters.counterpartyId,
    transactionFilters.dueOnly,
    deferredQuery,
  ])

  useEffect(() => {
    if (token) void loadLedger()
  }, [token])

  useEffect(() => {
    if (token && selectedLedgerId) void loadSelectedLedger()
  }, [token, selectedLedgerId])

  useEffect(() => {
    if (token) void loadReports()
  }, [token, reportRange.from, reportRange.to])

  useEffect(() => {
    if (token) void loadPlanner()
  }, [token])

  useEffect(() => {
    if (token) void loadHousehold()
  }, [token])

  useEffect(() => {
    if (token) void loadHistory()
  }, [token])

  useEffect(() => {
    if (token) void loadAttachments()
  }, [token, selectedTransaction?.id])

  useEffect(() => {
    if (!securityOverview?.hasPin || isLocked) {
      return
    }
    let timeoutId = window.setTimeout(() => setIsLocked(true), securityOverview.sessionTimeoutMinutes * 60 * 1000)
    const resetTimer = () => {
      window.clearTimeout(timeoutId)
      timeoutId = window.setTimeout(() => setIsLocked(true), securityOverview.sessionTimeoutMinutes * 60 * 1000)
    }
    window.addEventListener('pointerdown', resetTimer)
    window.addEventListener('keydown', resetTimer)
    return () => {
      window.clearTimeout(timeoutId)
      window.removeEventListener('pointerdown', resetTimer)
      window.removeEventListener('keydown', resetTimer)
    }
  }, [securityOverview, isLocked])

  useEffect(() => {
    if (!currentHousehold?.joined || !currentHousehold.household) {
      return
    }
    setTransactionDraft((current) => (current.householdId ? current : { ...current, householdId: currentHousehold.household?.id ?? '' }))
    setRecurringDraft((current) => (current.householdId ? current : { ...current, householdId: currentHousehold.household?.id ?? '' }))
  }, [currentHousehold])

  const refreshAll = async () => {
    await Promise.all([runLoadCore(), runLoadTransactions(), runLoadLedger(), runLoadReports(), runLoadPlanner(), runLoadHousehold(), runLoadHistory()])
    if (selectedLedgerId) await runLoadSelectedLedger()
    if (selectedTransaction) await runLoadAttachments()
  }

  const handleApiError = (error: unknown, clearSession = false) => {
    const message = error instanceof Error ? error.message : 'Something went wrong'
    notify('error', message)
    if (clearSession && message.toLowerCase().includes('unauthorized')) {
      setToken(null)
      setProfile(null)
      setIsLocked(false)
      replaceHistoryWithDashboard()
    }
  }

  const handleAuthSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setBusy(true)
    try {
      const response = await apiRequest<AuthResponse>(authMode === 'login' ? '/api/v1/auth/login' : '/api/v1/auth/register', {
        method: 'POST',
        body:
          authMode === 'login'
            ? { email: authDraft.email, password: authDraft.password }
            : authDraft,
      })
      setToken(response.token)
      setProfile(response.profile)
      notify('success', response.message)
    } catch (error) {
      handleApiError(error)
    } finally {
      setBusy(false)
    }
  }

  const handleSaveTransaction = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!token) return
    setBusy(true)
    try {
      const detail = await apiRequest<TransactionDetail>(transactionDraft.id ? `/api/v1/transactions/${transactionDraft.id}` : '/api/v1/transactions', {
        method: transactionDraft.id ? 'PUT' : 'POST',
        token,
        body: buildTransactionPayload(transactionDraft),
      })
      setSelectedTransaction(detail)
      setTransactionDraft(resetDraftForNext(settings?.defaultAccountId ?? accounts[0]?.id ?? ''))
      notify('success', transactionDraft.id ? 'Transaction updated' : 'Transaction saved')
      await refreshAll()
    } catch (error) {
      handleApiError(error)
    } finally {
      setBusy(false)
    }
  }

  const handleEditTransaction = async (id: string) => {
    if (!token) return
    try {
      const detail = await apiRequest<TransactionDetail>(`/api/v1/transactions/${id}`, { token })
      setSelectedTransaction(detail)
      setTransactionDraft(detailToDraft(detail, flatCategories))
      navigateToView('transactions')
    } catch (error) {
      handleApiError(error)
    }
  }

  const handleVoidTransaction = async (id: string) => {
    if (!token || !window.confirm('Void this transaction?')) return
    try {
      await apiRequest(`/api/v1/transactions/${id}`, { method: 'DELETE', token })
      setSelectedTransaction(null)
      setTransactionDraft(resetDraftForNext(settings?.defaultAccountId ?? accounts[0]?.id ?? ''))
      notify('success', 'Transaction voided')
      await refreshAll()
    } catch (error) {
      handleApiError(error)
    }
  }

  const handleSaveAccount = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!token) return
    try {
      await apiRequest<Account>('/api/v1/accounts', {
        method: 'POST',
        token,
        body: { ...accountDraft, openingBalance: Number(accountDraft.openingBalance || 0) },
      })
      setAccountDraft({ name: '', accountType: 'CASH', openingBalance: '0', accentColor: '#155eef', active: true })
      notify('success', 'Account saved')
      await runLoadCore()
    } catch (error) {
      handleApiError(error)
    }
  }

  const handleSaveCategory = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!token) return
    try {
      await apiRequest<CategoryNode>('/api/v1/categories', {
        method: 'POST',
        token,
        body: {
          ...categoryDraft,
          parentCategoryId: categoryDraft.parentCategoryId || null,
          sortOrder: Number(categoryDraft.sortOrder || 0),
        },
      })
      setCategoryDraft({ name: '', parentCategoryId: '', categoryKind: 'EXPENSE', sortOrder: '0', active: true })
      notify('success', 'Category saved')
      await runLoadCore()
    } catch (error) {
      handleApiError(error)
    }
  }

  const handleSaveCounterparty = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!token) return
    try {
      await apiRequest<Counterparty>('/api/v1/counterparties', { method: 'POST', token, body: counterpartyDraft })
      setCounterpartyDraft({ name: '', counterpartyType: 'MERCHANT', phone: '', notes: '', active: true })
      notify('success', 'Counterparty saved')
      await runLoadCore()
    } catch (error) {
      handleApiError(error)
    }
  }

  const handleSaveSettlement = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!token || !selectedTransaction) return
    try {
      await apiRequest('/api/v1/ledger/settlement', {
        method: 'POST',
        token,
        body: {
          baseTxnId: selectedTransaction.id,
          accountId: settlementDraft.accountId,
          amount: Number(settlementDraft.amount),
          transactionAt: new Date().toISOString(),
          note: settlementDraft.note || null,
        },
      })
      setSettlementDraft({ amount: '', accountId: settings?.defaultAccountId || accounts[0]?.id || '', note: '' })
      notify('success', 'Settlement recorded')
      await refreshAll()
    } catch (error) {
      handleApiError(error)
    }
  }

  const handleSaveSettings = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!token || !settings) return
    try {
      setSettings(await apiRequest<Settings>('/api/v1/settings', { method: 'PUT', token, body: settings }))
      notify('success', 'Preferences updated')
      await runLoadCore()
    } catch (error) {
      handleApiError(error)
    }
  }

  const handleSaveRecurring = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!token) return
    try {
      await apiRequest<RecurringRule>('/api/v1/planner/recurring', {
        method: 'POST',
        token,
        body: {
          id: recurringDraft.id,
          title: recurringDraft.title,
          transactionType: recurringDraft.transactionType,
          amount: Number(recurringDraft.amount || 0),
          accountId: recurringDraft.accountId || null,
          toAccountId: recurringDraft.toAccountId || null,
          categoryId: recurringDraft.categoryId || null,
          counterpartyId: recurringDraft.counterpartyId || null,
          frequencyType: recurringDraft.frequencyType,
          nextRunAt: toIsoFromLocalDateTime(recurringDraft.nextRunAt),
          intervalDays: Number(recurringDraft.intervalDays || 30),
          dueDateOffsetDays: Number(recurringDraft.dueDateOffsetDays || 0),
          remindDaysBefore: Number(recurringDraft.remindDaysBefore || 0),
          note: recurringDraft.note || null,
          referenceNo: recurringDraft.referenceNo || null,
          locationText: recurringDraft.locationText || null,
          autoCreate: recurringDraft.autoCreate,
          active: recurringDraft.active,
          shared: recurringDraft.shared,
          sharedParticipantCount: Number(recurringDraft.sharedParticipantCount || 1),
          householdId: recurringDraft.shared ? recurringDraft.householdId || null : null,
        },
      })
      setRecurringDraft({
        title: '',
        transactionType: 'EXPENSE',
        amount: '',
        accountId: defaultAccountId,
        toAccountId: '',
        categoryId: '',
        counterpartyId: '',
        frequencyType: 'MONTHLY',
        nextRunAt: toDateTimeLocalValue(new Date().toISOString()),
        intervalDays: '30',
        dueDateOffsetDays: '0',
        remindDaysBefore: '3',
        note: '',
        referenceNo: '',
        locationText: '',
        autoCreate: false,
        active: true,
        shared: false,
        sharedParticipantCount: '1',
        householdId: currentHousehold?.household?.id ?? '',
      })
      notify('success', 'Recurring rule saved')
      await runLoadPlanner()
    } catch (error) {
      handleApiError(error)
    }
  }

  const handleRunRecurring = async (id: string) => {
    if (!token) return
    try {
      await apiRequest<TransactionDetail>(`/api/v1/planner/recurring/${id}/run`, { method: 'POST', token })
      notify('success', 'Recurring entry created')
      await refreshAll()
    } catch (error) {
      handleApiError(error)
    }
  }

  const handleSaveBudget = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!token) return
    try {
      await apiRequest<Budget>('/api/v1/planner/budgets', {
        method: 'POST',
        token,
        body: {
          id: budgetDraft.id,
          title: budgetDraft.title,
          categoryId: budgetDraft.categoryId || null,
          amountLimit: Number(budgetDraft.amountLimit || 0),
          periodStart: budgetDraft.periodStart,
          periodEnd: budgetDraft.periodEnd,
          alertPercent: Number(budgetDraft.alertPercent || 80),
          rolloverEnabled: budgetDraft.rolloverEnabled,
          active: budgetDraft.active,
        },
      })
      setBudgetDraft({
        title: '',
        categoryId: '',
        amountLimit: '',
        periodStart: new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString().slice(0, 10),
        periodEnd: new Date().toISOString().slice(0, 10),
        alertPercent: '80',
        rolloverEnabled: false,
        active: true,
      })
      notify('success', 'Budget saved')
      await runLoadPlanner()
    } catch (error) {
      handleApiError(error)
    }
  }

  const handleSaveGoal = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!token) return
    try {
      await apiRequest<SavingsGoal>('/api/v1/planner/goals', {
        method: 'POST',
        token,
        body: {
          id: goalDraft.id,
          title: goalDraft.title,
          targetAmount: Number(goalDraft.targetAmount || 0),
          savedAmount: Number(goalDraft.savedAmount || 0),
          targetDate: goalDraft.targetDate || null,
          accountId: goalDraft.accountId || null,
          notes: goalDraft.notes || null,
          active: goalDraft.active,
        },
      })
      setGoalDraft({
        title: '',
        targetAmount: '',
        savedAmount: '0',
        targetDate: '',
        accountId: '',
        notes: '',
        active: true,
      })
      notify('success', 'Savings goal saved')
      await runLoadPlanner()
    } catch (error) {
      handleApiError(error)
    }
  }

  const handleAddAttachment = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!token || !selectedTransaction) return
    try {
      await apiRequest<Attachment>(`/api/v1/transactions/${selectedTransaction.id}/attachments`, {
        method: 'POST',
        token,
        body: {
          fileUrl: attachmentDraft.fileUrl,
          mimeType: attachmentDraft.mimeType || null,
          label: attachmentDraft.label || null,
        },
      })
      setAttachmentDraft({ fileUrl: '', mimeType: '', label: '' })
      notify('success', 'Attachment added')
      await runLoadAttachments()
    } catch (error) {
      handleApiError(error)
    }
  }

  const handleDeleteAttachment = async (attachmentId: string) => {
    if (!token || !selectedTransaction) return
    try {
      await apiRequest<MessageResponse>(`/api/v1/transactions/${selectedTransaction.id}/attachments/${attachmentId}`, { method: 'DELETE', token })
      notify('success', 'Attachment removed')
      await runLoadAttachments()
    } catch (error) {
      handleApiError(error)
    }
  }

  const handleExportTransactions = async () => {
    if (!token) return
    try {
      const csv = await apiTextRequest('/api/v1/data/export/transactions', { token })
      setExportedCsv(csv)
      notify('success', 'Transaction export is ready')
    } catch (error) {
      handleApiError(error)
    }
  }

  const handleImportTransactions = async (dryRun: boolean) => {
    if (!token || !importCsv.trim()) return
    try {
      const result = await apiRequest<{ importedCount: number; preview: { lineNumber: number; valid: boolean; message: string }[] }>(
        '/api/v1/data/import/transactions',
        {
          method: 'POST',
          token,
          body: { csvContent: importCsv, dryRun },
        },
      )
      setImportResult(result)
      notify('success', dryRun ? 'Import preview ready' : `Imported ${result.importedCount} rows`)
      if (!dryRun) {
        await refreshAll()
      }
    } catch (error) {
      handleApiError(error)
    }
  }

  const handleSetPin = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!token || !pinDraft) return
    try {
      await apiRequest<MessageResponse>('/api/v1/security/pin', { method: 'PUT', token, body: { pin: pinDraft } })
      setPinDraft('')
      notify('success', 'PIN saved')
      await runLoadCore()
    } catch (error) {
      handleApiError(error)
    }
  }

  const handleClearPin = async () => {
    if (!token) return
    try {
      await apiRequest<MessageResponse>('/api/v1/security/pin', { method: 'DELETE', token })
      setUnlockPin('')
      setIsLocked(false)
      notify('success', 'PIN removed')
      await runLoadCore()
    } catch (error) {
      handleApiError(error)
    }
  }

  const handleUnlock = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!token) return
    try {
      const result = await apiRequest<{ valid: boolean; message: string }>('/api/v1/security/pin/verify', {
        method: 'POST',
        token,
        body: { pin: unlockPin },
      })
      if (!result.valid) {
        notify('error', result.message)
        return
      }
      setUnlockPin('')
      setIsLocked(false)
      notify('success', 'Workspace unlocked')
    } catch (error) {
      handleApiError(error)
    }
  }

  const handleCreateHousehold = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!token || !householdCreateName.trim()) return
    try {
      await apiRequest<CurrentHousehold['household']>('/api/v1/household/create', {
        method: 'POST',
        token,
        body: { name: householdCreateName },
      })
      setHouseholdCreateName('')
      notify('success', 'Household created')
      await runLoadHousehold()
    } catch (error) {
      handleApiError(error)
    }
  }

  const handleJoinHousehold = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!token || !householdJoinCode.trim()) return
    try {
      await apiRequest<CurrentHousehold['household']>('/api/v1/household/join', {
        method: 'POST',
        token,
        body: { inviteCode: householdJoinCode },
      })
      setHouseholdJoinCode('')
      notify('success', 'Joined household')
      await refreshAll()
    } catch (error) {
      handleApiError(error)
    }
  }

  const handleLeaveHousehold = async () => {
    if (!token) return
    try {
      await apiRequest<MessageResponse>('/api/v1/household/leave', { method: 'POST', token })
      setTransactionDraft((current) => ({ ...current, shared: false, householdId: '' }))
      notify('success', 'Household updated')
      await refreshAll()
    } catch (error) {
      handleApiError(error)
    }
  }

  const handleDeactivateMaster = async (path: string, label: string) => {
    if (!token || !window.confirm(`Deactivate this ${label}?`)) return
    try {
      await apiRequest(path, { method: 'DELETE', token })
      notify('success', `${label} deactivated`)
      await runLoadCore()
    } catch (error) {
      handleApiError(error)
    }
  }

  const viewMeta = viewContent[activeView]
  const firstName = profile?.fullName?.split(' ')[0] ?? 'there'
  const defaultAccountId = settings?.defaultAccountId ?? activeAccounts[0]?.id ?? ''
  const defaultAccountName = activeAccounts.find((account) => account.id === defaultAccountId)?.name ?? 'No default account'
  const totalBalance = (dashboard?.cashBalance ?? 0) + (dashboard?.nonCashBalance ?? 0)
  const todayVsYesterday = (dashboard?.todayExpense ?? 0) - (dashboard?.yesterdayExpense ?? 0)
  const topCategoryFocus = dashboard?.topCategories[0]
  const selectedLedgerOpenCount = selectedLedger ? selectedLedger.transactions.filter((item) => item.outstandingAmount > 0).length : 0
  const activeNavLabel = navigationItems.find((item) => item.view === activeView)?.label ?? 'Dashboard'
  const canGoBack = historyIndex > 0
  const sharedTransactionEnabled = Boolean(currentHousehold?.joined && currentHousehold.household)
  const plannerHighlights = reminders.slice(0, 4)
  const sharedFeedCount = currentHousehold?.household?.recentSharedTransactions.length ?? 0

  const editRecurringRule = (rule: RecurringRule) => {
    setRecurringDraft({
      id: rule.id,
      title: rule.title,
      transactionType: rule.transactionType,
      amount: String(rule.amount),
      accountId: rule.accountId ?? '',
      toAccountId: rule.toAccountId ?? '',
      categoryId: rule.categoryId ?? '',
      counterpartyId: rule.counterpartyId ?? '',
      frequencyType: rule.frequencyType,
      nextRunAt: toDateTimeLocalValue(rule.nextRunAt),
      intervalDays: String(rule.intervalDays),
      dueDateOffsetDays: String(rule.dueDateOffsetDays),
      remindDaysBefore: String(rule.remindDaysBefore),
      note: rule.note ?? '',
      referenceNo: rule.referenceNo ?? '',
      locationText: rule.locationText ?? '',
      autoCreate: rule.autoCreate,
      active: rule.active,
      shared: rule.shared,
      sharedParticipantCount: String(rule.sharedParticipantCount),
      householdId: rule.householdId ?? '',
    })
  }

  const editBudget = (budget: Budget) => {
    setBudgetDraft({
      id: budget.id,
      title: budget.title,
      categoryId: budget.categoryId ?? '',
      amountLimit: String(budget.amountLimit),
      periodStart: budget.periodStart,
      periodEnd: budget.periodEnd,
      alertPercent: String(budget.alertPercent),
      rolloverEnabled: budget.rolloverEnabled,
      active: budget.active,
    })
  }

  const editGoal = (goal: SavingsGoal) => {
    setGoalDraft({
      id: goal.id,
      title: goal.title,
      targetAmount: String(goal.targetAmount),
      savedAmount: String(goal.savedAmount),
      targetDate: goal.targetDate ?? '',
      accountId: goal.accountId ?? '',
      notes: goal.notes ?? '',
      active: goal.active,
    })
  }

  const resetComposer = () => {
    setSelectedTransaction(null)
    setTransactionDraft(resetDraftForNext(defaultAccountId))
  }

  const navigateToView = (view: View, options: { replace?: boolean; masterTab?: MasterTab } = {}) => {
    const nextMasterTab = view === 'masters' ? options.masterTab ?? masterTab : masterTab
    const nextPath = buildRoutePath(appBasePath, view, nextMasterTab)
    const currentLocation = `${window.location.pathname}${window.location.search}`

    if (currentLocation === nextPath) {
      closeMobileNav()
      if (view === 'masters') {
        setMasterTab(nextMasterTab)
      }
      startTransition(() => setActiveView(view))
      return
    }

    const nextHistoryIndex = options.replace ? historyIndex : historyIndex + 1
    const nextState = { ...(window.history.state ?? {}), expenseHistoryIndex: nextHistoryIndex }

    if (options.replace) {
      window.history.replaceState(nextState, '', nextPath)
    } else {
      window.history.pushState(nextState, '', nextPath)
    }

    closeMobileNav()
    setHistoryIndex(nextHistoryIndex)
    if (view === 'masters') {
      setMasterTab(nextMasterTab)
    }
    startTransition(() => setActiveView(view))
  }

  const openView = (view: View) => {
    navigateToView(view)
  }

  const openMasters = (tab: MasterTab) => {
    navigateToView('masters', { masterTab: tab })
  }

  const applyQuickEntry = (type: TransactionType) => {
    setSelectedTransaction(null)
    setTransactionDraft((current) => ({
      ...current,
      transactionType: type,
      rootCategoryId: '',
      categoryId: '',
      baseTxnId: type.startsWith('REPAYMENT') ? current.baseTxnId : '',
    }))
    navigateToView('transactions')
  }

  const goBack = () => {
    if (!canGoBack) return
    window.history.back()
  }

  const applyTransactionRangePreset = (preset: 'today' | 'week' | 'month' | 'all') => {
    const range = getRangePreset(preset)
    setTransactionFilters((current) => ({
      ...current,
      ...range,
      page: 0,
    }))
  }

  const applyReportRangePreset = (preset: 'today' | 'month' | 'quarter' | 'all') => {
    setReportRange(getRangePreset(preset))
  }

  const loadMostRecentTransaction = async () => {
    const latest = dashboard?.recentTransactions[0]
    if (!latest) {
      notify('info', 'Add your first transaction to use this shortcut')
      return
    }
    await handleEditTransaction(latest.id)
  }

  if (!token || !profile) {
    return (
      <div className="auth-shell">
        <div className="auth-spotlight">
          <p className="eyebrow">LedgerLocal Pro</p>
          <h1>Personal finance that feels calm, fast, and professional.</h1>
          <p className="lead">
            Built for real daily life: quick spending entry, cleaner ledgers, overdue follow-up, and reports that stay useful instead of noisy.
          </p>
          <div className="feature-stack">
            <FeatureBadge label="Fast daily capture" />
            <FeatureBadge label="Dues and settlements" />
            <FeatureBadge label="Reports that stay readable" />
            <FeatureBadge label="Clean account-wise tracking" />
          </div>
          <div className="spotlight-grid">
            <div className="detail-card">
              <p className="eyebrow">Why it feels easier</p>
              <h3>Less friction for repeat entry</h3>
              <p>Quick presets, clearer forms, and strong defaults reduce the work needed for routine transactions.</p>
            </div>
            <div className="detail-card">
              <p className="eyebrow">Made for daily use</p>
              <h3>See money movement at a glance</h3>
              <p>Balances, overdue items, recent activity, and top categories stay visible from the moment you sign in.</p>
            </div>
          </div>
        </div>
        <form className="auth-card" onSubmit={handleAuthSubmit}>
          <div className="card-header">
            <p className="eyebrow">{authMode === 'login' ? 'Secure Login' : 'Create Account'}</p>
            <h2>{authMode === 'login' ? 'Open your finance workspace' : 'Create your workspace'}</h2>
            <p className="muted">
              {authMode === 'login'
                ? "Jump back into today's flow in a few seconds."
                : 'Set up your personal ledger and start entering clean data from day one.'}
            </p>
          </div>
          {banner ? <div className={`banner ${banner.tone}`}>{banner.text}</div> : null}
          {authMode === 'register' ? (
            <label className="field">
              <span className="field-label">Full name</span>
              <input placeholder="Your name" value={authDraft.fullName} onChange={(event) => setAuthDraft((c) => ({ ...c, fullName: event.target.value }))} required />
            </label>
          ) : null}
          <label className="field">
            <span className="field-label">Email</span>
            <input type="email" placeholder="you@example.com" value={authDraft.email} onChange={(event) => setAuthDraft((c) => ({ ...c, email: event.target.value }))} required />
          </label>
          {authMode === 'register' ? (
            <label className="field">
              <span className="field-label">Mobile</span>
              <input placeholder="Optional contact number" value={authDraft.mobile} onChange={(event) => setAuthDraft((c) => ({ ...c, mobile: event.target.value }))} />
            </label>
          ) : null}
          <label className="field">
            <span className="field-label">Password</span>
            <input type="password" placeholder="Enter password" value={authDraft.password} onChange={(event) => setAuthDraft((c) => ({ ...c, password: event.target.value }))} required />
          </label>
          <button className="primary-button" disabled={busy}>{busy ? 'Working...' : authMode === 'login' ? 'Sign in' : 'Create account'}</button>
          <button className="ghost-button" type="button" onClick={() => setAuthDraft((c) => ({ ...c, email: 'demo@ledgerlocal.app', password: 'demo1234' }))}>Use demo account</button>
          <button className="text-button" type="button" onClick={() => setAuthMode((m) => (m === 'login' ? 'register' : 'login'))}>
            {authMode === 'login' ? 'Need a new account?' : 'Already have an account?'}
          </button>
        </form>
      </div>
    )
  }

  if (isLocked) {
    return (
      <div className="lock-shell">
        <form className="auth-card lock-card" onSubmit={handleUnlock}>
          <div className="card-header">
            <p className="eyebrow">Session locked</p>
            <h2>Unlock your finance workspace</h2>
            <p className="muted">Enter your PIN to continue.</p>
          </div>
          {banner ? <div className={`banner ${banner.tone}`}>{banner.text}</div> : null}
          <label className="field">
            <span className="field-label">PIN</span>
            <input type="password" inputMode="numeric" placeholder="Enter PIN" value={unlockPin} onChange={(event) => setUnlockPin(event.target.value)} />
          </label>
          <button className="primary-button" disabled={busy || !unlockPin}>
            Unlock
          </button>
        </form>
      </div>
    )
  }

  return (
    <div className="app-shell">
      <button
        className={`sidebar-backdrop ${isMobileNavOpen ? 'open' : ''}`}
        type="button"
        aria-label="Close navigation menu"
        aria-hidden={!isMobileNavOpen}
        tabIndex={isMobileNavOpen ? 0 : -1}
        onClick={closeMobileNav}
      />
      <aside className={`sidebar ${isMobileNavOpen ? 'open' : ''}`} id="app-sidebar">
        <div className="sidebar-top">
          <div className="brand">
            <div className="brand-mark">Rs</div>
            <div>
              <p className="eyebrow">LedgerLocal Pro</p>
              <h2>Daily Finance Desk</h2>
            </div>
          </div>
          <button className="sidebar-close" type="button" aria-label="Close navigation menu" onClick={closeMobileNav}>
            <span />
            <span />
          </button>
        </div>
        <div className="sidebar-scroll">
          <div className="sidebar-summary">
            <span className="muted">Liquid balance</span>
            <strong>{formatCurrency(totalBalance)}</strong>
            <p>Default account: {defaultAccountName}</p>
          </div>
          <nav className="nav-stack">
            {navigationItems.map((item) => (
              <button key={item.view} className={`nav-button ${activeView === item.view ? 'active' : ''}`} onClick={() => openView(item.view)} type="button">
                <div className="nav-copy">
                  <strong>{item.label}</strong>
                  <span>{item.description}</span>
                </div>
              </button>
            ))}
          </nav>
          <div className="sidebar-shortcuts">
            <p className="eyebrow">Quick actions</p>
            <button className="ghost-button" type="button" onClick={() => applyQuickEntry('EXPENSE')}>
              New expense
            </button>
            <button className="ghost-button" type="button" onClick={() => openView('ledger')}>
              Open dues
            </button>
            <button className="ghost-button" type="button" onClick={() => openMasters('counterparties')}>
              Add merchant or person
            </button>
          </div>
          <div className="sidebar-foot">
            <p>{profile.fullName}</p>
            <span>{profile.email}</span>
            <button className="ghost-button" type="button" onClick={() => { setToken(null); setProfile(null); setIsLocked(false); replaceHistoryWithDashboard() }}>
              Sign out
            </button>
          </div>
        </div>
      </aside>

      <main className="workspace">
        <header className="workspace-header">
          <div className="header-copy">
            <div className="header-topline">
              <div className="mobile-nav-shell">
                <button
                  className="mobile-nav-trigger"
                  type="button"
                  aria-label={isMobileNavOpen ? 'Close navigation menu' : 'Open navigation menu'}
                  aria-expanded={isMobileNavOpen}
                  aria-controls="app-sidebar"
                  onClick={toggleMobileNav}
                >
                  <span />
                  <span />
                  <span />
                </button>
                <span className="mobile-nav-caption">{activeNavLabel}</span>
              </div>
              {canGoBack ? (
                <button className="ghost-button back-button" type="button" onClick={goBack}>
                  Back
                </button>
              ) : null}
              <p className="eyebrow">{viewMeta.eyebrow}</p>
            </div>
            <h1>{viewMeta.title}</h1>
            <p className="lead">{viewMeta.description}</p>
            <div className="chip-row">
              <span className="pill neutral">{activeAccounts.length} active accounts</span>
              <span className="pill neutral">{activeCounterparties.length} active counterparties</span>
              <span className="pill neutral">{dashboard?.overdueCount ?? 0} overdue items</span>
            </div>
          </div>
          <div className="header-spotlight">
            <div className="focus-card">
              <span className="muted">Welcome back, {firstName}</span>
              <strong>{formatCurrency(totalBalance)}</strong>
              <p>
                {todayVsYesterday > 0
                  ? `You have spent ${formatCurrency(todayVsYesterday)} more than yesterday.`
                  : todayVsYesterday < 0
                    ? `You are down ${formatCurrency(Math.abs(todayVsYesterday))} versus yesterday.`
                    : 'Today is tracking exactly like yesterday so far.'}
              </p>
              <div className="focus-grid">
                <div>
                  <span>Top category</span>
                  <strong>{topCategoryFocus?.label ?? 'No data yet'}</strong>
                </div>
                <div>
                  <span>Open dues</span>
                  <strong>{receivable.length + payable.length}</strong>
                </div>
              </div>
            </div>
            <div className="header-actions">
              <button className="ghost-button" type="button" onClick={resetComposer}>Reset entry</button>
              {securityOverview?.hasPin ? <button className="ghost-button" type="button" onClick={() => setIsLocked(true)}>Lock now</button> : null}
              <button className="primary-button" type="button" onClick={() => openView('transactions')}>Open full entry</button>
            </div>
          </div>
        </header>

        {banner ? <div className={`banner ${banner.tone}`}>{banner.text}</div> : null}
        {bootstrapping ? <div className="loading-strip">Refreshing your finance workspace...</div> : null}

        {activeView === 'dashboard' ? (
          <section className="view-grid">
            <section className="hero-panel panel dashboard-hero">
              <div className="dashboard-story">
                <div className="card-header">
                  <p className="eyebrow">Daily capture</p>
                  <h2>Faster entry, better context</h2>
                </div>
                <div className="insight-card">
                  <strong>{formatCurrency(dashboard?.todayExpense ?? 0)}</strong>
                  <p>spent today across all tracked accounts.</p>
                  <span>{topCategoryFocus ? `${topCategoryFocus.label} is currently your biggest category.` : 'Your top category will appear here once entries start landing.'}</span>
                </div>
                <div className="shortcut-grid">
                  {quickEntryPresets.map((item) => (
                    <button key={item.type} className="shortcut-card" type="button" onClick={() => applyQuickEntry(item.type)}>
                      <strong>{item.label}</strong>
                      <span>{item.hint}</span>
                    </button>
                  ))}
                </div>
                <div className="chip-row">
                  {dashboard?.topCategories.slice(0, 5).map((item) => (
                    <button key={item.label} className="chip-button" type="button" onClick={() => applyTopCategory(item.label, activeRootCategories, setTransactionDraft)}>
                      {item.label}
                    </button>
                  ))}
                </div>
              </div>
              <div className="quick-add-card">
                <div className="card-header">
                  <p className="eyebrow">Quick add</p>
                  <h2>Capture the next entry now</h2>
                </div>
                <TransactionForm
                  draft={transactionDraft}
                  rootCategories={filteredRoots}
                  childCategories={childCategories}
                  accounts={accounts}
                  counterparties={availableCounterparties}
                  showCategory={transactionUsesCategory(transactionDraft.transactionType)}
                  showCounterparty={showCounterparty(transactionDraft.transactionType)}
                  showFromAccount={usesFromAccount(transactionDraft.transactionType)}
                  showToAccount={usesToAccount(transactionDraft.transactionType)}
                  showSettlementFields={transactionDraft.transactionType === 'REPAYMENT_IN' || transactionDraft.transactionType === 'REPAYMENT_OUT'}
                  onChange={setTransactionDraft}
                  onSubmit={handleSaveTransaction}
                  submitting={busy}
                  compact
                />
              </div>
            </section>
            <section className="metrics-grid">
              <MetricCard label="Today spent" value={dashboard?.todayExpense ?? 0} />
              <MetricCard label="Month spent" value={dashboard?.currentMonthExpense ?? 0} accent="warning" />
              <MetricCard label="To receive" value={dashboard?.receivableOutstanding ?? 0} accent="good" />
              <MetricCard label="To pay" value={dashboard?.payableOutstanding ?? 0} accent="alert" />
              <MetricCard label="Cash balance" value={dashboard?.cashBalance ?? 0} accent="neutral" />
              <MetricCard label="Bank + UPI" value={dashboard?.nonCashBalance ?? 0} />
            </section>
            <div className="dashboard-split">
              <Panel title="Accounts at a glance" eyebrow="Balance pockets">
                <div className="table-shell">
                  {activeAccounts.length ? activeAccounts.map((account) => (
                    <div key={account.id} className="table-row">
                      <div className="table-main">
                        <div className="table-title-row">
                          <strong>{account.name}</strong>
                          <span className="pill neutral">{account.accountType}</span>
                        </div>
                        <p>Opening balance {formatCurrency(account.openingBalance)}</p>
                      </div>
                      <strong>{formatCurrency(account.currentBalance)}</strong>
                    </div>
                  )) : <EmptyState title="No active accounts" description="Add at least one account so quick entry can default cleanly." />}
                </div>
              </Panel>
              <Panel title="Smart shortcuts" eyebrow="One-click jumps">
                <div className="shortcut-grid compact-grid">
                  <button className="shortcut-card" type="button" onClick={loadMostRecentTransaction}>
                    <strong>Reuse latest entry</strong>
                    <span>Start from your most recent transaction and adjust only what changed.</span>
                  </button>
                  <button className="shortcut-card" type="button" onClick={() => openView('ledger')}>
                    <strong>Review open dues</strong>
                    <span>Open receivable and payable ledgers without leaving the main workspace.</span>
                  </button>
                  <button className="shortcut-card" type="button" onClick={() => openMasters('categories')}>
                    <strong>Add a category</strong>
                    <span>Keep reports clean by creating missing categories as soon as you notice the gap.</span>
                  </button>
                  <button className="shortcut-card" type="button" onClick={() => openView('reports')}>
                    <strong>Open reports</strong>
                    <span>Switch to category and cash-flow views when something feels off in the day.</span>
                  </button>
                </div>
              </Panel>
            </div>
            <Panel title="Recent transactions" eyebrow="Operational view">
              <TransactionTable rows={dashboard?.recentTransactions ?? []} onEdit={handleEditTransaction} />
            </Panel>
            <Panel title="Dues overview" eyebrow="Receivable and payable">
              <div className="dues-grid">
                <SimpleList title="Receivable" items={receivable} onOpen={(id) => { setSelectedLedgerId(id); openView('ledger') }} />
                <SimpleList title="Payable" items={payable} onOpen={(id) => { setSelectedLedgerId(id); openView('ledger') }} />
              </div>
            </Panel>
            <Panel title="Reminders and watchlist" eyebrow="What needs attention next">
              {plannerHighlights.length ? (
                <div className="table-shell">
                  {plannerHighlights.map((item) => (
                    <div key={`${item.title}-${item.message}-${item.dueDate ?? 'na'}`} className="table-row">
                      <div className="table-main">
                        <div className="table-title-row">
                          <strong>{item.title}</strong>
                          <span className={`pill ${item.severity === 'high' ? '' : 'neutral'}`}>{item.severity}</span>
                        </div>
                        <p>{item.message}</p>
                        {item.dueDate ? <p className="muted">Due {new Date(item.dueDate).toLocaleDateString('en-IN')}</p> : null}
                      </div>
                      <strong>{item.amount ? formatCurrency(item.amount) : '-'}</strong>
                    </div>
                  ))}
                </div>
              ) : <EmptyState title="No active reminders" description="Recurring items, budgets, goals, and due dates will surface here once they need action." />}
            </Panel>
          </section>
        ) : null}

        {activeView === 'transactions' ? (
          <section className="view-grid dual">
            <Panel title={transactionDraft.id ? 'Edit transaction' : 'Transaction composer'} eyebrow="Daily-ready capture">
              <div className="shortcut-grid compact-grid">
                {quickEntryPresets.map((item) => (
                  <button key={item.type} className={`shortcut-card ${transactionDraft.transactionType === item.type ? 'active' : ''}`} type="button" onClick={() => applyQuickEntry(item.type)}>
                    <strong>{item.label}</strong>
                    <span>{item.hint}</span>
                  </button>
                ))}
              </div>
              <TransactionForm
                draft={transactionDraft}
                rootCategories={filteredRoots}
                childCategories={childCategories}
                accounts={accounts}
                counterparties={availableCounterparties}
                showCategory={transactionUsesCategory(transactionDraft.transactionType)}
                showCounterparty={showCounterparty(transactionDraft.transactionType)}
                showFromAccount={usesFromAccount(transactionDraft.transactionType)}
                showToAccount={usesToAccount(transactionDraft.transactionType)}
                showSettlementFields={transactionDraft.transactionType === 'REPAYMENT_IN' || transactionDraft.transactionType === 'REPAYMENT_OUT'}
                onChange={setTransactionDraft}
                onSubmit={handleSaveTransaction}
                submitting={busy}
              />
              <div className="detail-card">
                <p className="eyebrow">Shared finance</p>
                <h3>Household-ready transaction</h3>
                <label className="checkbox-line">
                  <input
                    type="checkbox"
                    checked={transactionDraft.shared}
                    disabled={!sharedTransactionEnabled}
                    onChange={(event) =>
                      setTransactionDraft((current) => ({
                        ...current,
                        shared: event.target.checked,
                        householdId: event.target.checked ? currentHousehold?.household?.id ?? current.householdId : '',
                      }))
                    }
                  />
                  Mark this transaction as shared
                </label>
                <div className="form-grid">
                  <label className="field">
                    <span className="field-label">Household</span>
                    <select
                      value={transactionDraft.householdId}
                      disabled={!transactionDraft.shared || !sharedTransactionEnabled}
                      onChange={(event) => setTransactionDraft((current) => ({ ...current, householdId: event.target.value }))}
                    >
                      <option value="">Choose household</option>
                      {householdOptions.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
                    </select>
                  </label>
                  <label className="field">
                    <span className="field-label">Participants</span>
                    <input
                      type="number"
                      min="1"
                      value={transactionDraft.sharedParticipantCount}
                      disabled={!transactionDraft.shared}
                      onChange={(event) => setTransactionDraft((current) => ({ ...current, sharedParticipantCount: event.target.value }))}
                    />
                  </label>
                </div>
                {!sharedTransactionEnabled ? <p className="muted">Create or join a household first to enable shared expense tracking.</p> : null}
              </div>
              <div className="inline-actions">
                <button className="ghost-button" type="button" onClick={loadMostRecentTransaction}>
                  Use latest entry as starting point
                </button>
                <button className="ghost-button" type="button" onClick={resetComposer}>
                  Clear current draft
                </button>
              </div>
              {selectedTransaction ? (
                <div className="detail-card">
                  <p className="eyebrow">Selected transaction</p>
                  <h3>{selectedTransaction.categoryPath || selectedTransaction.counterpartyName || selectedTransaction.transactionType}</h3>
                  <p>{selectedTransaction.note || 'No note saved for this transaction.'}</p>
                  <div className="chip-row">
                    <span className="pill neutral">{formatCurrency(selectedTransaction.amount)}</span>
                    {selectedTransaction.outstandingAmount > 0 ? <span className="pill">{formatCurrency(selectedTransaction.outstandingAmount)} open</span> : null}
                    {selectedTransaction.shared ? <span className="pill neutral">Shared with {selectedTransaction.sharedParticipantCount} people</span> : null}
                  </div>
                </div>
              ) : null}
              {selectedTransaction ? (
                <div className="detail-card">
                  <div className="card-header">
                    <p className="eyebrow">Receipts and links</p>
                    <h3>Attachments for the selected transaction</h3>
                  </div>
                  <form className="form-grid" onSubmit={handleAddAttachment}>
                    <label className="field span-two">
                      <span className="field-label">File URL</span>
                      <input placeholder="https://..." value={attachmentDraft.fileUrl} onChange={(event) => setAttachmentDraft((current) => ({ ...current, fileUrl: event.target.value }))} required />
                    </label>
                    <label className="field">
                      <span className="field-label">Label</span>
                      <input placeholder="Receipt, invoice, warranty..." value={attachmentDraft.label} onChange={(event) => setAttachmentDraft((current) => ({ ...current, label: event.target.value }))} />
                    </label>
                    <label className="field">
                      <span className="field-label">Mime type</span>
                      <input placeholder="image/jpeg or application/pdf" value={attachmentDraft.mimeType} onChange={(event) => setAttachmentDraft((current) => ({ ...current, mimeType: event.target.value }))} />
                    </label>
                    <button className="primary-button" type="submit">Add attachment</button>
                  </form>
                  {attachments.length ? (
                    <div className="table-shell">
                      {attachments.map((attachment) => (
                        <div key={attachment.id} className="table-row">
                          <div className="table-main">
                            <strong>{attachment.label || 'Attachment'}</strong>
                            <p>{attachment.fileUrl}</p>
                            <p className="muted">{attachment.mimeType || 'Unknown type'}</p>
                          </div>
                          <div className="row-actions">
                            <a className="ghost-button" href={attachment.fileUrl} target="_blank" rel="noreferrer">Open</a>
                            <button className="ghost-button" type="button" onClick={() => handleDeleteAttachment(attachment.id)}>Remove</button>
                          </div>
                        </div>
                      ))}
                    </div>
                  ) : <EmptyState title="No attachments yet" description="Add receipt links, invoice URLs, or supporting files to keep context with the entry." />}
                </div>
              ) : null}
              {selectedTransaction ? <button className="danger-button" type="button" onClick={() => handleVoidTransaction(selectedTransaction.id)}>Void selected transaction</button> : null}
            </Panel>
            <Panel title="Transaction explorer" eyebrow="Search and filter">
              <div className="filter-presets">
                <button className="chip-button" type="button" onClick={() => applyTransactionRangePreset('today')}>Today</button>
                <button className="chip-button" type="button" onClick={() => applyTransactionRangePreset('week')}>Last 7 days</button>
                <button className="chip-button" type="button" onClick={() => applyTransactionRangePreset('month')}>This month</button>
                <button className="chip-button" type="button" onClick={() => applyTransactionRangePreset('all')}>All time</button>
                <button className="ghost-button" type="button" onClick={() => setTransactionFilters(defaultFilters())}>Clear filters</button>
              </div>
              <div className="filter-grid">
                <input placeholder="Search notes, merchant, category" value={transactionFilters.query} onChange={(event) => setTransactionFilters((c) => ({ ...c, query: event.target.value, page: 0 }))} />
                <select value={transactionFilters.transactionType} onChange={(event) => setTransactionFilters((c) => ({ ...c, transactionType: event.target.value as TransactionFilters['transactionType'], page: 0 }))}>
                  <option value="ALL">All types</option>
                  {['EXPENSE', 'INCOME', 'TRANSFER', 'LEND', 'BORROW', 'REPAYMENT_IN', 'REPAYMENT_OUT'].map((item) => <option key={item} value={item}>{item}</option>)}
                </select>
                <select value={transactionFilters.accountId} onChange={(event) => setTransactionFilters((c) => ({ ...c, accountId: event.target.value, page: 0 }))}>
                  <option value="">All accounts</option>
                  {accounts.map((account) => <option key={account.id} value={account.id}>{account.name}</option>)}
                </select>
                <select value={transactionFilters.categoryId} onChange={(event) => setTransactionFilters((c) => ({ ...c, categoryId: event.target.value, page: 0 }))}>
                  <option value="">All categories</option>
                  {categoryFilterOptions.map((category) => <option key={category.id} value={category.id}>{category.label}</option>)}
                </select>
                <select value={transactionFilters.counterpartyId} onChange={(event) => setTransactionFilters((c) => ({ ...c, counterpartyId: event.target.value, page: 0 }))}>
                  <option value="">All people and merchants</option>
                  {activeCounterparties.map((counterparty) => <option key={counterparty.id} value={counterparty.id}>{counterparty.name}</option>)}
                </select>
                <input type="date" value={transactionFilters.from} onChange={(event) => setTransactionFilters((c) => ({ ...c, from: event.target.value, page: 0 }))} />
                <input type="date" value={transactionFilters.to} onChange={(event) => setTransactionFilters((c) => ({ ...c, to: event.target.value, page: 0 }))} />
                <select value={String(transactionFilters.size)} onChange={(event) => setTransactionFilters((c) => ({ ...c, size: Number(event.target.value), page: 0 }))}>
                  {[10, 20, 50].map((size) => <option key={size} value={size}>{size} per page</option>)}
                </select>
                <label className="checkbox-line"><input type="checkbox" checked={transactionFilters.dueOnly} onChange={(event) => setTransactionFilters((c) => ({ ...c, dueOnly: event.target.checked, page: 0 }))} />Due only</label>
              </div>
              <div className="results-summary">
                <span>{transactionsPage?.totalElements ?? 0} matching entries</span>
                <span>Page {(transactionsPage?.page ?? 0) + 1} of {Math.max(transactionsPage?.totalPages ?? 1, 1)}</span>
              </div>
              <TransactionTable rows={transactionsPage?.content ?? []} onEdit={handleEditTransaction} />
              <div className="pager">
                <button className="ghost-button" type="button" disabled={(transactionsPage?.page ?? 0) === 0} onClick={() => setTransactionFilters((c) => ({ ...c, page: Math.max(c.page - 1, 0) }))}>Previous</button>
                <span>Page {(transactionsPage?.page ?? 0) + 1} of {Math.max(transactionsPage?.totalPages ?? 1, 1)}</span>
                <button className="ghost-button" type="button" disabled={!transactionsPage || transactionsPage.page >= Math.max(transactionsPage.totalPages - 1, 0)} onClick={() => setTransactionFilters((c) => ({ ...c, page: c.page + 1 }))}>Next</button>
              </div>
            </Panel>
          </section>
        ) : null}

        {activeView === 'planner' ? (
          <section className="view-grid dual">
            <Panel title="Recurring and budgets" eyebrow="Automate the routine">
              <div className="detail-card">
                <div className="card-header">
                  <p className="eyebrow">Recurring rule</p>
                  <h3>{recurringDraft.id ? 'Edit recurring rule' : 'Create recurring rule'}</h3>
                </div>
                <form className="form-grid" onSubmit={handleSaveRecurring}>
                  <label className="field">
                    <span className="field-label">Title</span>
                    <input value={recurringDraft.title} onChange={(event) => setRecurringDraft((current) => ({ ...current, title: event.target.value }))} required />
                  </label>
                  <label className="field">
                    <span className="field-label">Type</span>
                    <select value={recurringDraft.transactionType} onChange={(event) => setRecurringDraft((current) => ({ ...current, transactionType: event.target.value as TransactionType }))}>
                      {['EXPENSE', 'INCOME', 'TRANSFER', 'LEND', 'BORROW', 'REPAYMENT_IN', 'REPAYMENT_OUT'].map((item) => <option key={item} value={item}>{item}</option>)}
                    </select>
                  </label>
                  <label className="field">
                    <span className="field-label">Amount</span>
                    <input type="number" min="0.01" step="0.01" value={recurringDraft.amount} onChange={(event) => setRecurringDraft((current) => ({ ...current, amount: event.target.value }))} required />
                  </label>
                  <label className="field">
                    <span className="field-label">Frequency</span>
                    <select value={recurringDraft.frequencyType} onChange={(event) => setRecurringDraft((current) => ({ ...current, frequencyType: event.target.value as RecurringDraft['frequencyType'] }))}>
                      {['DAILY', 'WEEKLY', 'MONTHLY', 'CUSTOM_DAYS'].map((item) => <option key={item} value={item}>{item}</option>)}
                    </select>
                  </label>
                  <label className="field">
                    <span className="field-label">Primary account</span>
                    <select value={recurringDraft.accountId} onChange={(event) => setRecurringDraft((current) => ({ ...current, accountId: event.target.value }))}>
                      <option value="">Choose account</option>
                      {activeAccounts.map((account) => <option key={account.id} value={account.id}>{account.name}</option>)}
                    </select>
                  </label>
                  <label className="field">
                    <span className="field-label">Second account</span>
                    <select value={recurringDraft.toAccountId} onChange={(event) => setRecurringDraft((current) => ({ ...current, toAccountId: event.target.value }))}>
                      <option value="">Optional</option>
                      {activeAccounts.map((account) => <option key={account.id} value={account.id}>{account.name}</option>)}
                    </select>
                  </label>
                  <label className="field">
                    <span className="field-label">Category</span>
                    <select value={recurringDraft.categoryId} onChange={(event) => setRecurringDraft((current) => ({ ...current, categoryId: event.target.value }))}>
                      <option value="">Optional</option>
                      {categoryFilterOptions.map((category) => <option key={category.id} value={category.id}>{category.label}</option>)}
                    </select>
                  </label>
                  <label className="field">
                    <span className="field-label">Counterparty</span>
                    <select value={recurringDraft.counterpartyId} onChange={(event) => setRecurringDraft((current) => ({ ...current, counterpartyId: event.target.value }))}>
                      <option value="">Optional</option>
                      {activeCounterparties.map((counterparty) => <option key={counterparty.id} value={counterparty.id}>{counterparty.name}</option>)}
                    </select>
                  </label>
                  <label className="field span-two">
                    <span className="field-label">Next run</span>
                    <input type="datetime-local" value={recurringDraft.nextRunAt} onChange={(event) => setRecurringDraft((current) => ({ ...current, nextRunAt: event.target.value }))} required />
                  </label>
                  <label className="field">
                    <span className="field-label">Custom interval days</span>
                    <input type="number" min="1" value={recurringDraft.intervalDays} onChange={(event) => setRecurringDraft((current) => ({ ...current, intervalDays: event.target.value }))} />
                  </label>
                  <label className="field">
                    <span className="field-label">Remind before (days)</span>
                    <input type="number" min="0" value={recurringDraft.remindDaysBefore} onChange={(event) => setRecurringDraft((current) => ({ ...current, remindDaysBefore: event.target.value }))} />
                  </label>
                  <label className="checkbox-line"><input type="checkbox" checked={recurringDraft.autoCreate} onChange={(event) => setRecurringDraft((current) => ({ ...current, autoCreate: event.target.checked }))} />Auto create when due</label>
                  <label className="checkbox-line"><input type="checkbox" checked={recurringDraft.shared} disabled={!sharedTransactionEnabled} onChange={(event) => setRecurringDraft((current) => ({ ...current, shared: event.target.checked, householdId: event.target.checked ? currentHousehold?.household?.id ?? current.householdId : '' }))} />Shared household rule</label>
                  <button className="primary-button" type="submit">Save recurring rule</button>
                </form>
              </div>
              <div className="detail-card">
                <div className="card-header">
                  <p className="eyebrow">Budget</p>
                  <h3>{budgetDraft.id ? 'Edit budget' : 'Create budget limit'}</h3>
                </div>
                <form className="form-grid" onSubmit={handleSaveBudget}>
                  <label className="field">
                    <span className="field-label">Title</span>
                    <input value={budgetDraft.title} onChange={(event) => setBudgetDraft((current) => ({ ...current, title: event.target.value }))} required />
                  </label>
                  <label className="field">
                    <span className="field-label">Category</span>
                    <select value={budgetDraft.categoryId} onChange={(event) => setBudgetDraft((current) => ({ ...current, categoryId: event.target.value }))}>
                      <option value="">All expenses</option>
                      {categoryFilterOptions.map((category) => <option key={category.id} value={category.id}>{category.label}</option>)}
                    </select>
                  </label>
                  <label className="field">
                    <span className="field-label">Limit</span>
                    <input type="number" min="0.01" step="0.01" value={budgetDraft.amountLimit} onChange={(event) => setBudgetDraft((current) => ({ ...current, amountLimit: event.target.value }))} required />
                  </label>
                  <label className="field">
                    <span className="field-label">Alert %</span>
                    <input type="number" min="1" max="200" value={budgetDraft.alertPercent} onChange={(event) => setBudgetDraft((current) => ({ ...current, alertPercent: event.target.value }))} />
                  </label>
                  <label className="field">
                    <span className="field-label">Start</span>
                    <input type="date" value={budgetDraft.periodStart} onChange={(event) => setBudgetDraft((current) => ({ ...current, periodStart: event.target.value }))} required />
                  </label>
                  <label className="field">
                    <span className="field-label">End</span>
                    <input type="date" value={budgetDraft.periodEnd} onChange={(event) => setBudgetDraft((current) => ({ ...current, periodEnd: event.target.value }))} required />
                  </label>
                  <label className="checkbox-line"><input type="checkbox" checked={budgetDraft.rolloverEnabled} onChange={(event) => setBudgetDraft((current) => ({ ...current, rolloverEnabled: event.target.checked }))} />Enable rollover note</label>
                  <button className="primary-button" type="submit">Save budget</button>
                </form>
              </div>
            </Panel>
            <Panel title="Goals, reminders, and schedule" eyebrow="Keep future plans visible">
              <div className="detail-card">
                <div className="card-header">
                  <p className="eyebrow">Savings goal</p>
                  <h3>{goalDraft.id ? 'Edit goal' : 'Create savings goal'}</h3>
                </div>
                <form className="form-grid" onSubmit={handleSaveGoal}>
                  <label className="field">
                    <span className="field-label">Title</span>
                    <input value={goalDraft.title} onChange={(event) => setGoalDraft((current) => ({ ...current, title: event.target.value }))} required />
                  </label>
                  <label className="field">
                    <span className="field-label">Target amount</span>
                    <input type="number" min="0.01" step="0.01" value={goalDraft.targetAmount} onChange={(event) => setGoalDraft((current) => ({ ...current, targetAmount: event.target.value }))} required />
                  </label>
                  <label className="field">
                    <span className="field-label">Saved so far</span>
                    <input type="number" min="0" step="0.01" value={goalDraft.savedAmount} onChange={(event) => setGoalDraft((current) => ({ ...current, savedAmount: event.target.value }))} />
                  </label>
                  <label className="field">
                    <span className="field-label">Target date</span>
                    <input type="date" value={goalDraft.targetDate} onChange={(event) => setGoalDraft((current) => ({ ...current, targetDate: event.target.value }))} />
                  </label>
                  <label className="field span-two">
                    <span className="field-label">Notes</span>
                    <textarea rows={4} value={goalDraft.notes} onChange={(event) => setGoalDraft((current) => ({ ...current, notes: event.target.value }))} />
                  </label>
                  <button className="primary-button" type="submit">Save goal</button>
                </form>
              </div>
              <div className="table-shell">
                {calendarItems.length ? calendarItems.map((item) => (
                  <div key={`${item.kind}-${item.title}-${item.dueDate}`} className="table-row">
                    <div className="table-main">
                      <div className="table-title-row">
                        <strong>{item.title}</strong>
                        <span className="pill neutral">{item.kind}</span>
                      </div>
                      <p>{item.detail}</p>
                      <p className="muted">{new Date(item.dueDate).toLocaleDateString('en-IN')}</p>
                    </div>
                    <strong>{formatCurrency(item.amount)}</strong>
                  </div>
                )) : <EmptyState title="Nothing scheduled yet" description="Recurring rules, goals, and due items will appear on this planning calendar." />}
              </div>
              <div className="table-shell">
                {reminders.length ? reminders.map((item) => (
                  <div key={`${item.title}-${item.message}-${item.dueDate ?? 'na'}`} className="table-row">
                    <div className="table-main">
                      <div className="table-title-row">
                        <strong>{item.title}</strong>
                        <span className={`pill ${item.severity === 'high' ? '' : 'neutral'}`}>{item.severity}</span>
                      </div>
                      <p>{item.message}</p>
                    </div>
                    <strong>{item.amount ? formatCurrency(item.amount) : '-'}</strong>
                  </div>
                )) : <EmptyState title="All clear" description="Budget alerts, due follow-ups, and recurring reminders will show here when they matter." />}
              </div>
            </Panel>
            <Panel title="Recurring rules" eyebrow="Run or edit scheduled items">
              {recurringRules.length ? (
                <div className="table-shell">
                  {recurringRules.map((rule) => (
                    <div key={rule.id} className="table-row">
                      <div className="table-main">
                        <div className="table-title-row">
                          <strong>{rule.title}</strong>
                          <span className="pill neutral">{rule.frequencyType}</span>
                          {rule.shared ? <span className="pill neutral">Shared</span> : null}
                        </div>
                        <p>{rule.transactionType} - Next run {new Date(rule.nextRunAt).toLocaleString('en-IN')}</p>
                        <p>{rule.note || 'No note saved.'}</p>
                      </div>
                      <div className="row-actions">
                        <strong>{formatCurrency(rule.amount)}</strong>
                        <button className="ghost-button" type="button" onClick={() => editRecurringRule(rule)}>Edit</button>
                        <button className="ghost-button" type="button" onClick={() => handleRunRecurring(rule.id)}>Run now</button>
                      </div>
                    </div>
                  ))}
                </div>
              ) : <EmptyState title="No recurring rules yet" description="Start with rent, subscriptions, salary, or EMI so your routine money movement becomes predictable." />}
            </Panel>
            <Panel title="Budgets and goals" eyebrow="Limits and savings progress">
              <div className="table-shell">
                {budgets.map((budget) => (
                  <div key={budget.id} className="table-row">
                    <div className="table-main">
                      <div className="table-title-row">
                        <strong>{budget.title}</strong>
                        <span className={`pill ${budget.usagePercent >= 100 ? '' : 'neutral'}`}>{Math.round(budget.usagePercent)}%</span>
                      </div>
                      <p>{budget.categoryName} - {budget.periodStart} to {budget.periodEnd}</p>
                      <p>{formatCurrency(budget.spentAmount)} spent - {formatCurrency(budget.remainingAmount)} remaining</p>
                    </div>
                    <div className="row-actions">
                      <strong>{formatCurrency(budget.amountLimit)}</strong>
                      <button className="ghost-button" type="button" onClick={() => editBudget(budget)}>Edit</button>
                    </div>
                  </div>
                ))}
                {goals.map((goal) => (
                  <div key={goal.id} className="table-row">
                    <div className="table-main">
                      <div className="table-title-row">
                        <strong>{goal.title}</strong>
                        <span className="pill neutral">{Math.round(goal.progressPercent)}%</span>
                      </div>
                      <p>{formatCurrency(goal.savedAmount)} saved out of {formatCurrency(goal.targetAmount)}</p>
                      <p>{goal.targetDate ? `Target date ${goal.targetDate}` : 'No target date yet'}</p>
                    </div>
                    <div className="row-actions">
                      <button className="ghost-button" type="button" onClick={() => editGoal(goal)}>Edit</button>
                    </div>
                  </div>
                ))}
              </div>
            </Panel>
          </section>
        ) : null}

        {activeView === 'ledger' ? (
          <section className="view-grid dual">
            <Panel title="Receivable and payable" eyebrow="Person-wise outstanding">
              <div className="metrics-grid compact">
                <MetricCard label="Total receivable" value={dashboard?.receivableOutstanding ?? 0} accent="good" small />
                <MetricCard label="Total payable" value={dashboard?.payableOutstanding ?? 0} accent="alert" small />
              </div>
              <div className="dues-grid">
                <SimpleList title="Receivable" items={receivable} onOpen={setSelectedLedgerId} activeId={selectedLedgerId} />
                <SimpleList title="Payable" items={payable} onOpen={setSelectedLedgerId} activeId={selectedLedgerId} />
              </div>
            </Panel>
            <Panel title={selectedLedger?.counterpartyName ?? 'Counterparty ledger'} eyebrow="History and settlement">
              {selectedLedger ? (
                <>
                  <div className="chip-row">
                    <span className="pill neutral">{selectedLedgerOpenCount} open item(s)</span>
                    <span className="pill neutral">{selectedLedger.transactions.length} total entries</span>
                  </div>
                  <div className="metrics-grid compact">
                    <MetricCard label="To receive" value={selectedLedger.receivableOutstanding} accent="good" small />
                    <MetricCard label="To pay" value={selectedLedger.payableOutstanding} accent="alert" small />
                  </div>
                  <div className="detail-card">
                    <div className="card-header">
                      <p className="eyebrow">Fast settlement</p>
                      <h3>Close open dues without leaving the ledger</h3>
                    </div>
                    <form className="mini-form" onSubmit={handleSaveSettlement}>
                      <label className="field">
                        <span className="field-label">Base transaction</span>
                        <select value={selectedTransaction?.id ?? ''} onChange={(event) => setSelectedTransaction(selectedLedger.transactions.find((item) => item.id === event.target.value) ?? null)}>
                          <option value="">Select base transaction</option>
                          {selectedLedger.transactions.filter((item) => item.outstandingAmount > 0).map((item) => <option key={item.id} value={item.id}>{item.transactionType} - {formatCurrency(item.outstandingAmount)}</option>)}
                        </select>
                      </label>
                      <label className="field">
                        <span className="field-label">Settlement account</span>
                        <select value={settlementDraft.accountId} onChange={(event) => setSettlementDraft((c) => ({ ...c, accountId: event.target.value }))}>
                          <option value="">Choose account</option>
                          {accounts.filter((account) => account.active).map((account) => <option key={account.id} value={account.id}>{account.name}</option>)}
                        </select>
                      </label>
                      <label className="field">
                        <span className="field-label">Amount</span>
                        <input type="number" min="0.01" step="0.01" placeholder="0.00" value={settlementDraft.amount} onChange={(event) => setSettlementDraft((c) => ({ ...c, amount: event.target.value }))} />
                      </label>
                      <label className="field">
                        <span className="field-label">Note</span>
                        <input placeholder="Optional note" value={settlementDraft.note} onChange={(event) => setSettlementDraft((c) => ({ ...c, note: event.target.value }))} />
                      </label>
                      <button className="primary-button" disabled={busy || !selectedTransaction}>Record settlement</button>
                    </form>
                  </div>
                  <TransactionTable rows={selectedLedger.transactions} onEdit={handleEditTransaction} showSettlements />
                </>
              ) : <EmptyState title="Pick a person" description="Select a receivable or payable row to inspect the ledger." />}
            </Panel>
          </section>
        ) : null}

        {activeView === 'masters' ? (
          <section className="view-grid dual">
            <Panel title="Master data" eyebrow="Keep repeated inputs clean">
              <div className="summary-strip">
                <div className="detail-card">
                  <p className="eyebrow">Accounts</p>
                  <h3>{activeAccounts.length}</h3>
                  <p>Active funding sources ready to use.</p>
                </div>
                <div className="detail-card">
                  <p className="eyebrow">Categories</p>
                  <h3>{rootCategories.length}</h3>
                  <p>Top-level buckets currently available.</p>
                </div>
                <div className="detail-card">
                  <p className="eyebrow">People / merchants</p>
                  <h3>{counterparties.length}</h3>
                  <p>Reusable counterparties for faster entry.</p>
                </div>
              </div>
              <div className="tab-row">
                {(['accounts', 'categories', 'counterparties'] as MasterTab[]).map((tab) => (
                  <button key={tab} className={`tab-button ${masterTab === tab ? 'active' : ''}`} type="button" onClick={() => openMasters(tab)}>
                    {tab}
                  </button>
                ))}
              </div>
              {masterTab === 'accounts' ? (
                <form className="form-grid" onSubmit={handleSaveAccount}>
                  <label className="field">
                    <span className="field-label">Account name</span>
                    <input placeholder="Cash wallet, salary bank, credit card..." value={accountDraft.name} onChange={(event) => setAccountDraft((c) => ({ ...c, name: event.target.value }))} required />
                  </label>
                  <label className="field">
                    <span className="field-label">Account type</span>
                    <select value={accountDraft.accountType} onChange={(event) => setAccountDraft((c) => ({ ...c, accountType: event.target.value }))}>{['CASH', 'BANK', 'UPI', 'WALLET', 'CREDIT_CARD', 'LOAN'].map((item) => <option key={item}>{item}</option>)}</select>
                  </label>
                  <label className="field">
                    <span className="field-label">Opening balance</span>
                    <input type="number" min="0" step="0.01" value={accountDraft.openingBalance} onChange={(event) => setAccountDraft((c) => ({ ...c, openingBalance: event.target.value }))} />
                  </label>
                  <label className="field">
                    <span className="field-label">Accent color</span>
                    <input type="color" value={accountDraft.accentColor} onChange={(event) => setAccountDraft((c) => ({ ...c, accentColor: event.target.value }))} />
                  </label>
                  <button className="primary-button" type="submit">Save account</button>
                </form>
              ) : null}
              {masterTab === 'categories' ? (
                <form className="form-grid" onSubmit={handleSaveCategory}>
                  <label className="field">
                    <span className="field-label">Category name</span>
                    <input placeholder="Groceries, rent, salary, fuel..." value={categoryDraft.name} onChange={(event) => setCategoryDraft((c) => ({ ...c, name: event.target.value }))} required />
                  </label>
                  <label className="field">
                    <span className="field-label">Parent category</span>
                    <select value={categoryDraft.parentCategoryId} onChange={(event) => setCategoryDraft((c) => ({ ...c, parentCategoryId: event.target.value }))}>
                      <option value="">Top-level</option>
                      {activeRootCategories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
                    </select>
                  </label>
                  <label className="field">
                    <span className="field-label">Category kind</span>
                    <select value={categoryDraft.categoryKind} onChange={(event) => setCategoryDraft((c) => ({ ...c, categoryKind: event.target.value }))}>
                      <option value="EXPENSE">Expense</option>
                      <option value="INCOME">Income</option>
                      <option value="SHARED">Shared</option>
                    </select>
                  </label>
                  <label className="field">
                    <span className="field-label">Sort order</span>
                    <input type="number" min="0" value={categoryDraft.sortOrder} onChange={(event) => setCategoryDraft((c) => ({ ...c, sortOrder: event.target.value }))} />
                  </label>
                  <button className="primary-button" type="submit">Save category</button>
                </form>
              ) : null}
              {masterTab === 'counterparties' ? (
                <form className="form-grid" onSubmit={handleSaveCounterparty}>
                  <label className="field">
                    <span className="field-label">Merchant or person</span>
                    <input placeholder="Store, friend, landlord, client..." value={counterpartyDraft.name} onChange={(event) => setCounterpartyDraft((c) => ({ ...c, name: event.target.value }))} required />
                  </label>
                  <label className="field">
                    <span className="field-label">Type</span>
                    <select value={counterpartyDraft.counterpartyType} onChange={(event) => setCounterpartyDraft((c) => ({ ...c, counterpartyType: event.target.value }))}>
                      <option value="MERCHANT">Merchant</option>
                      <option value="PERSON">Person</option>
                      <option value="BOTH">Both</option>
                    </select>
                  </label>
                  <label className="field">
                    <span className="field-label">Phone</span>
                    <input placeholder="Optional contact number" value={counterpartyDraft.phone} onChange={(event) => setCounterpartyDraft((c) => ({ ...c, phone: event.target.value }))} />
                  </label>
                  <label className="field span-two">
                    <span className="field-label">Notes</span>
                    <textarea rows={3} placeholder="Helpful identifier or context" value={counterpartyDraft.notes} onChange={(event) => setCounterpartyDraft((c) => ({ ...c, notes: event.target.value }))} />
                  </label>
                  <button className="primary-button" type="submit">Save counterparty</button>
                </form>
              ) : null}
            </Panel>
            <Panel title="Existing records" eyebrow="Ready to reuse">
              {masterTab === 'accounts' ? (
                <div className="table-shell">
                  {accounts.map((account) => (
                    <div key={account.id} className="table-row">
                      <div className="table-main">
                        <div className="table-title-row">
                          <strong>{account.name}</strong>
                          <span className="pill neutral">{account.accountType}</span>
                        </div>
                        <p>Opening balance {formatCurrency(account.openingBalance)}</p>
                      </div>
                      <div className="row-actions">
                        <strong>{formatCurrency(account.currentBalance)}</strong>
                        {account.active ? <button className="ghost-button" type="button" onClick={() => handleDeactivateMaster(`/api/v1/accounts/${account.id}`, 'account')}>Deactivate</button> : <span className="pill">Inactive</span>}
                      </div>
                    </div>
                  ))}
                </div>
              ) : null}
              {masterTab === 'categories' ? (
                <div className="table-shell">
                  {rootCategories.map((category) => (
                    <div key={category.id} className="master-column">
                      <div className="table-row">
                        <div className="table-main">
                          <div className="table-title-row">
                            <strong>{category.name}</strong>
                            <span className="pill neutral">{category.categoryKind}</span>
                          </div>
                          <p>{category.active ? 'Active and ready for new entries.' : 'Inactive and hidden from new entry flows.'}</p>
                        </div>
                        {category.active ? <button className="ghost-button" type="button" onClick={() => handleDeactivateMaster(`/api/v1/categories/${category.id}`, 'category')}>Deactivate</button> : <span className="pill">Inactive</span>}
                      </div>
                      <div className="chip-row">
                        {category.children.map((child) => <span key={child.id} className={`chip ${child.active ? '' : 'muted-chip'}`}>{child.name}</span>)}
                      </div>
                    </div>
                  ))}
                </div>
              ) : null}
              {masterTab === 'counterparties' ? (
                <div className="table-shell">
                  {counterparties.map((item) => (
                    <div key={item.id} className="table-row">
                      <div className="table-main">
                        <div className="table-title-row">
                          <strong>{item.name}</strong>
                          <span className="pill neutral">{item.counterpartyType}</span>
                        </div>
                        <p>{item.notes || 'No notes saved yet.'}</p>
                      </div>
                      <div className="row-actions">
                        <button className="ghost-button" type="button" onClick={() => { setSelectedLedgerId(item.id); openView('ledger') }}>
                          Open ledger
                        </button>
                        {item.active ? <button className="ghost-button" type="button" onClick={() => handleDeactivateMaster(`/api/v1/counterparties/${item.id}`, 'counterparty')}>Deactivate</button> : <span className="pill">Inactive</span>}
                      </div>
                    </div>
                  ))}
                </div>
              ) : null}
            </Panel>
          </section>
        ) : null}

        {activeView === 'reports' ? (
          <section className="view-grid">
            <Panel title="Report filters" eyebrow="Date-range drill down">
              <div className="filter-presets">
                <button className="chip-button" type="button" onClick={() => applyReportRangePreset('today')}>Today</button>
                <button className="chip-button" type="button" onClick={() => applyReportRangePreset('month')}>This month</button>
                <button className="chip-button" type="button" onClick={() => applyReportRangePreset('quarter')}>Last 90 days</button>
                <button className="chip-button" type="button" onClick={() => applyReportRangePreset('all')}>All time</button>
              </div>
              <div className="filter-grid">
                <input type="date" value={reportRange.from} onChange={(event) => setReportRange((c) => ({ ...c, from: event.target.value }))} />
                <input type="date" value={reportRange.to} onChange={(event) => setReportRange((c) => ({ ...c, to: event.target.value }))} />
              </div>
              <div className="chip-row">
                <span className="pill neutral">Top category: {reports.categories[0]?.label ?? 'No data yet'}</span>
                <span className="pill neutral">Top account: {reports.accounts[0]?.label ?? 'No data yet'}</span>
                <span className="pill neutral">Top counterparty: {reports.counterparties[0]?.label ?? 'No data yet'}</span>
              </div>
            </Panel>
            <section className="metrics-grid">
              <MetricCard label="Current period expense" value={comparison?.currentExpense ?? 0} />
              <MetricCard label="Previous period expense" value={comparison?.previousExpense ?? 0} accent="neutral" />
              <MetricCard label="Delta" value={comparison?.deltaAmount ?? 0} accent={((comparison?.deltaAmount ?? 0) <= 0 ? 'good' : 'alert')} />
              <MetricCard label="Projected month expense" value={forecast?.projectedMonthExpense ?? 0} accent="warning" />
              <MetricCard label="Projected month income" value={forecast?.projectedMonthIncome ?? 0} accent="good" />
              <MetricCard label="Projected month net" value={forecast?.projectedMonthNet ?? 0} accent={((forecast?.projectedMonthNet ?? 0) >= 0 ? 'good' : 'alert')} />
            </section>
            <Panel title="Category summary" eyebrow="Where money is going"><SummaryGrid items={reports.categories} /></Panel>
            <Panel title="Subcategory summary" eyebrow="Drill-down detail"><SummaryGrid items={reports.subcategories} /></Panel>
            <Panel title="Account movement" eyebrow="Net movement by account"><SummaryGrid items={reports.accounts} /></Panel>
            <Panel title="Merchant and person summary" eyebrow="Highest concentration"><SummaryGrid items={reports.counterparties} /></Panel>
            <Panel title="Cash-flow timeline" eyebrow="Incoming versus outgoing"><TimelineGrid items={reports.cashFlow} /></Panel>
          </section>
        ) : null}

        {activeView === 'household' ? (
          <section className="view-grid dual">
            {!currentHousehold?.joined || !currentHousehold.household ? (
              <>
                <Panel title="Create household" eyebrow="Start shared finance">
                  <form className="form-grid" onSubmit={handleCreateHousehold}>
                    <label className="field span-two">
                      <span className="field-label">Household name</span>
                      <input placeholder="Home, family, flatmates..." value={householdCreateName} onChange={(event) => setHouseholdCreateName(event.target.value)} required />
                    </label>
                    <button className="primary-button" type="submit">Create household</button>
                  </form>
                </Panel>
                <Panel title="Join household" eyebrow="Use an invite code">
                  <form className="form-grid" onSubmit={handleJoinHousehold}>
                    <label className="field span-two">
                      <span className="field-label">Invite code</span>
                      <input placeholder="ABCDEFGH" value={householdJoinCode} onChange={(event) => setHouseholdJoinCode(event.target.value.toUpperCase())} required />
                    </label>
                    <button className="primary-button" type="submit">Join household</button>
                  </form>
                </Panel>
              </>
            ) : (
              <>
                <Panel title={currentHousehold.household.name} eyebrow="Shared workspace">
                  <div className="chip-row">
                    <span className="pill neutral">Invite code: {currentHousehold.household.inviteCode}</span>
                    <span className="pill neutral">{currentHousehold.household.members.length} member(s)</span>
                    <span className="pill neutral">{sharedFeedCount} shared entries</span>
                  </div>
                  <div className="table-shell">
                    {currentHousehold.household.members.map((member) => (
                      <div key={member.userId} className="table-row">
                        <div className="table-main">
                          <strong>{member.fullName}</strong>
                          <p>{member.email}</p>
                        </div>
                        <span className="pill neutral">{member.role}</span>
                      </div>
                    ))}
                  </div>
                  <button className="ghost-button" type="button" onClick={handleLeaveHousehold}>Leave household</button>
                </Panel>
                <Panel title="Shared transactions" eyebrow="Split visibility">
                  {currentHousehold.household.recentSharedTransactions.length ? (
                    <div className="table-shell">
                      {currentHousehold.household.recentSharedTransactions.map((item) => (
                        <div key={item.transactionId} className="table-row">
                          <div className="table-main">
                            <div className="table-title-row">
                              <strong>{item.ownerName}</strong>
                              <span className="pill neutral">{item.transactionType}</span>
                            </div>
                            <p>{item.note || 'No note saved for this shared transaction.'}</p>
                            <p className="muted">{new Date(item.transactionAt).toLocaleString('en-IN')}</p>
                          </div>
                          <div className="row-actions">
                            <span className="pill neutral">{item.participantCount} people</span>
                            <strong>{formatCurrency(item.estimatedShare)}</strong>
                          </div>
                        </div>
                      ))}
                    </div>
                  ) : <EmptyState title="No shared transactions yet" description="Mark transactions as shared to build a household feed and split estimate." />}
                </Panel>
              </>
            )}
          </section>
        ) : null}

        {activeView === 'history' ? (
          <section className="view-grid">
            <Panel title="Audit history" eyebrow="Recent activity">
              {auditEntries.length ? (
                <div className="table-shell">
                  {auditEntries.map((entry) => (
                    <div key={entry.id} className="table-row">
                      <div className="table-main">
                        <div className="table-title-row">
                          <strong>{entry.entityName}</strong>
                          <span className="pill neutral">{entry.actionName}</span>
                        </div>
                        <p>{new Date(entry.createdAt).toLocaleString('en-IN')}</p>
                        <p className="muted">Old: {truncateText(entry.oldValue)}</p>
                        <p className="muted">New: {truncateText(entry.newValue)}</p>
                      </div>
                      <span className="pill neutral">{entry.entityId.slice(0, 8)}</span>
                    </div>
                  ))}
                </div>
              ) : <EmptyState title="No audit entries yet" description="Create, update, or deactivate records and the history will appear here." />}
            </Panel>
          </section>
        ) : null}

        {activeView === 'settings' ? (
          <section className="view-grid dual">
            <Panel title="Preferences" eyebrow="Personal defaults">
              {settings ? (
                <form className="form-grid" onSubmit={handleSaveSettings}>
                  <label className="field">
                    <span className="field-label">Full name</span>
                    <input value={profile.fullName} disabled />
                  </label>
                  <label className="field">
                    <span className="field-label">Email</span>
                    <input value={profile.email} disabled />
                  </label>
                  <label className="field">
                    <span className="field-label">Default account</span>
                    <select value={settings.defaultAccountId ?? ''} onChange={(event) => setSettings((c) => c ? { ...c, defaultAccountId: event.target.value || null } : c)}>
                      <option value="">No default account</option>
                      {accounts.map((account) => <option key={account.id} value={account.id}>{account.name}</option>)}
                    </select>
                  </label>
                  <label className="field">
                    <span className="field-label">Default currency</span>
                    <input value={settings.defaultCurrency} onChange={(event) => setSettings((c) => c ? { ...c, defaultCurrency: event.target.value.toUpperCase() } : c)} />
                  </label>
                  <label className="field">
                    <span className="field-label">Date format</span>
                    <input value={settings.dateFormat} onChange={(event) => setSettings((c) => c ? { ...c, dateFormat: event.target.value } : c)} />
                  </label>
                  <label className="field">
                    <span className="field-label">Session timeout (minutes)</span>
                    <input type="number" min="5" value={settings.sessionTimeoutMinutes} onChange={(event) => setSettings((c) => c ? { ...c, sessionTimeoutMinutes: Number(event.target.value) } : c)} />
                  </label>
                  <label className="checkbox-line"><input type="checkbox" checked={settings.biometricEnabled} onChange={(event) => setSettings((c) => c ? { ...c, biometricEnabled: event.target.checked } : c)} />Biometric-ready flag</label>
                  <label className="checkbox-line"><input type="checkbox" checked={settings.reminderEnabled} onChange={(event) => setSettings((c) => c ? { ...c, reminderEnabled: event.target.checked } : c)} />Enable reminders</label>
                  <button className="primary-button" type="submit">Save preferences</button>
                </form>
              ) : null}
            </Panel>
            <Panel title="What makes the app easier now" eyebrow="Daily-life workflow improvements">
              <div className="spotlight-grid">
                <div className="detail-card">
                  <p className="eyebrow">Faster entry</p>
                  <h3>Quick presets and smarter forms</h3>
                  <p>Common transaction types, current-time shortcuts, amount chips, and stronger labeling reduce hesitation while entering data.</p>
                </div>
                <div className="detail-card">
                  <p className="eyebrow">Better oversight</p>
                  <h3>Stronger hierarchy across screens</h3>
                  <p>Balances, overdue items, account pockets, and transaction counts stay visible so you can act without hunting around.</p>
                </div>
                <div className="detail-card">
                  <p className="eyebrow">Cleaner maintenance</p>
                  <h3>Reusable masters stay closer to the flow</h3>
                  <p>Accounts, categories, and counterparties are easier to update, which keeps reports and daily entry dependable.</p>
                </div>
                <div className="detail-card">
                  <p className="eyebrow">Still practical</p>
                  <h3>Everything remains lightweight</h3>
                  <p>The app stays quick to operate without turning into a cluttered dashboard full of hard-to-use controls.</p>
                </div>
              </div>
            </Panel>
            <Panel title="Security tools" eyebrow="PIN and session control">
              <div className="chip-row">
                <span className="pill neutral">{securityOverview?.hasPin ? 'PIN enabled' : 'PIN not set'}</span>
                <span className="pill neutral">Session timeout {securityOverview?.sessionTimeoutMinutes ?? settings?.sessionTimeoutMinutes ?? 0} min</span>
              </div>
              <form className="form-grid" onSubmit={handleSetPin}>
                <label className="field span-two">
                  <span className="field-label">Set or replace PIN</span>
                  <input type="password" inputMode="numeric" placeholder="4 to 8 digits" value={pinDraft} onChange={(event) => setPinDraft(event.target.value)} />
                </label>
                <button className="primary-button" type="submit">Save PIN</button>
                <button className="ghost-button" type="button" onClick={handleClearPin} disabled={!securityOverview?.hasPin}>Remove PIN</button>
              </form>
            </Panel>
            <Panel title="Import and export" eyebrow="Move your data safely">
              <div className="inline-actions">
                <button className="ghost-button" type="button" onClick={handleExportTransactions}>Generate transaction export</button>
                <button className="ghost-button" type="button" onClick={() => handleImportTransactions(true)} disabled={!importCsv.trim()}>Preview import</button>
                <button className="primary-button" type="button" onClick={() => handleImportTransactions(false)} disabled={!importCsv.trim()}>Import rows</button>
              </div>
              <label className="field">
                <span className="field-label">Import CSV</span>
                <textarea rows={8} value={importCsv} onChange={(event) => setImportCsv(event.target.value)} placeholder="Paste CSV exported from this app or any file using the same header columns." />
              </label>
              <label className="field">
                <span className="field-label">Latest export</span>
                <textarea rows={8} value={exportedCsv} readOnly placeholder="Your generated CSV export will appear here." />
              </label>
              {importResult ? (
                <div className="table-shell">
                  {importResult.preview.map((row) => (
                    <div key={`${row.lineNumber}-${row.message}`} className="table-row">
                      <div className="table-main">
                        <strong>Line {row.lineNumber}</strong>
                        <p>{row.message}</p>
                      </div>
                      <span className={`pill ${row.valid ? 'neutral' : ''}`}>{row.valid ? 'Valid' : 'Error'}</span>
                    </div>
                  ))}
                </div>
              ) : null}
            </Panel>
          </section>
        ) : null}
      </main>
    </div>
  )
}

function showCounterparty(type: TransactionType) {
  return transactionNeedsCounterparty(type)
}

export default App
