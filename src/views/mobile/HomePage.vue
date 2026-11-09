<template>
    <f7-page ptr @ptr:refresh="reload" @page:afterin="onPageAfterIn" @page:beforeout="pendingPageActive = false">
        <f7-navbar>
            <f7-nav-title :title="tt('global.app.title')"></f7-nav-title>
        </f7-navbar>

        <ledger-context-notice :limited="!isDefaultLedger" />
        <div class="mobile-pending-entry"><LedgerPendingPanel compact :active="pendingPageActive" @open="openPendingTarget" /></div>
        <f7-block v-if="loadError" role="alert">{{ loadError }} <f7-link @click="init()">重试</f7-link></f7-block>
        <f7-block v-if="permissionError" role="alert">{{ permissionError }} <f7-link @click="init()">重试权限读取</f7-link></f7-block>
        <f7-block v-if="!isDefaultLedger && ledgerRole === LedgerMemberRole.Viewer">当前成员仅可查看流水，不能记账。</f7-block>
        <template v-if="!isDefaultLedger">
            <f7-block v-if="loading" role="status">正在加载当前账本…</f7-block>
            <f7-card v-else-if="scopedOverview" :title="scopedOverview.ledgerName">
                <f7-card-content>
                    <p>{{ scopedOverview.accountCount }} 个账户 · {{ scopedOverview.transactionCount }} 条流水</p>
                    <p v-for="balance in scopedOverview.balances" :key="balance.currency">
                        {{ balance.currency }}：{{ formatMobileWalletBalance(String(balance.balance), balance.currency) }}
                    </p>
                    <p v-if="!scopedOverview.balances.length">此账本尚无账户余额。</p>
                    <f7-link href="/account/list">查看账户</f7-link>
                    <f7-link class="margin-inline-start" href="/transaction/list">查看流水与导入账单</f7-link>
                </f7-card-content>
            </f7-card>
        </template>

        <f7-block class="mobile-ledger-actions">
            <f7-link href="/ledger/manage" class="mobile-ledger-action" aria-label="管理账本"><f7-icon f7="book"></f7-icon><span>账本</span></f7-link>
            <f7-link href="/savings/goals" class="mobile-ledger-action" aria-label="查看存钱计划"><f7-icon f7="money_dollar_circle"></f7-icon><span>存钱计划</span></f7-link>
            <f7-link href="/settings/budget" class="mobile-ledger-action" aria-label="查看当前账本预算"><f7-icon f7="chart_pie"></f7-icon><span>月度预算</span></f7-link>
        </f7-block>

        <div class="mobile-monthly-overview">
            <MonthlyOverviewPanel :scope="monthlyOverviewScope" :ledger-name="ledgersStore.selectedLedger.name"
                                  :username="userStore.currentUserBasicInfo?.username ?? ''" :show-amounts="showAmountInHomePage"
                                  :can-manage-budget="canWriteLedger" :active="pendingPageActive"
                                  @month="monthlyOverviewMonth = $event" @timezone="monthlyOverviewTimezone = $event"
                                  @report="openMonthlyReport" @budget="openMonthlyBudget" />
        </div>

        <template v-if="isDefaultLedger">
        <section v-if="!loading && !loadError && !mobileWalletAccounts.length" class="mobile-onboarding" aria-labelledby="mobile-onboarding-title">
            <div class="mobile-onboarding-icon"><f7-icon f7="wallet_pass"></f7-icon></div>
            <div class="mobile-onboarding-copy">
                <small>开始使用 · 0 / 1</small>
                <strong id="mobile-onboarding-title">先添加一个账户</strong>
                <span>填写当前余额后，才能准确记录支出与资金流转。</span>
            </div>
            <f7-link href="/account/add" class="mobile-onboarding-action" aria-label="添加首个账户">
                添加
            </f7-link>
        </section>

        <section class="mobile-wallet" aria-labelledby="mobile-wallet-title">
            <div class="mobile-section-heading">
                <div>
                    <small>DIGITAL WALLET</small>
                    <strong id="mobile-wallet-title">我的钱包</strong>
                </div>
                <f7-link href="/account/list">管理</f7-link>
            </div>
            <div v-if="loading" class="mobile-wallet-skeleton skeleton-text" aria-label="正在加载账户">
                <span></span><span></span>
            </div>
            <div v-else-if="mobileWalletAccounts.length" class="mobile-wallet-stack">
                <f7-link v-for="account in mobileWalletAccounts" :key="account.id" href="/account/list"
                         class="mobile-wallet-card" :class="mobileWalletCardClass(account.category)"
                         :aria-label="`查看账户 ${account.name}`">
                    <span class="mobile-wallet-card-top">
                        <small>{{ mobileWalletCategoryLabel(account.category) }}</small>
                        <f7-icon :f7="account.isLiability ? 'creditcard' : 'wave_3_right'"></f7-icon>
                    </span>
                    <span class="mobile-wallet-chip" aria-hidden="true"></span>
                    <span class="mobile-wallet-card-bottom">
                        <span><small>{{ account.isLiability ? '待还金额' : '可用余额' }}</small><strong>{{ formatMobileWalletBalance(account.balance, account.currency) }}</strong></span>
                        <b>{{ account.name }}</b>
                    </span>
                </f7-link>
            </div>
            <f7-link v-else href="/account/add" class="mobile-wallet-empty">
                <f7-icon f7="plus"></f7-icon>
                <span><strong>添加账户卡</strong><small>微信零钱、银行卡、信用卡或现金</small></span>
                <f7-icon f7="chevron_right"></f7-icon>
            </f7-link>
        </section>

        <overview-dashboard :layout="layout" :loading="loading" @navigate="onNavigate" />

        </template>

        <f7-toolbar tabbar icons bottom class="main-tabbar">
            <f7-link class="link" href="/transaction/list" :aria-label="tt('Details')">
                <f7-icon f7="square_list" aria-hidden="true"></f7-icon>
                <span class="tabbar-label">{{ tt('Details') }}</span>
            </f7-link>
            <f7-link class="link" href="/account/list" :aria-label="tt('Accounts')">
                <f7-icon f7="creditcard" aria-hidden="true"></f7-icon>
                <span class="tabbar-label">{{ tt('Accounts') }}</span>
            </f7-link>
            <!-- "homepage-add-button" must have the "dragenabled" class, otherwise the popover disappears immediately after the second long press -->
            <f7-link id="homepage-add-button" class="link dragenabled"
                     :href="canWriteLedger ? `/transaction/add?ledgerId=${ledgersStore.selectedLedgerId}` : '#'" :class="{ disabled: !canWriteLedger }" :aria-disabled="!canWriteLedger" aria-label="在当前账本记账" @taphold="openTransactionTemplatePopover">
                <f7-icon f7="plus_square" class="ebk-tarbar-big-icon" aria-hidden="true"></f7-icon>
            </f7-link>
            <f7-link class="link" href="/statistic/transaction" :aria-label="tt('Statistics')">
                <f7-icon f7="chart_pie" aria-hidden="true"></f7-icon>
                <span class="tabbar-label">{{ tt('Statistics') }}</span>
            </f7-link>
            <f7-link class="link" href="/settings" :aria-label="tt('Settings')">
                <f7-icon f7="gear_alt" aria-hidden="true"></f7-icon>
                <span class="tabbar-label">{{ tt('Settings') }}</span>
            </f7-link>
        </f7-toolbar>

        <f7-popover v-if="aiSourceAvailable || (isDefaultLedger && allTransactionTemplates && allTransactionTemplates.length)" class="template-popover-menu" target-el="#homepage-add-button"
                    v-model:opened="showTransactionTemplatePopover">
            <f7-list dividers v-if="aiSourceAvailable || hasTransactionAddMenuItems">
                <f7-list-item key="AIClipboardTextRecognition" link="#" no-chevron popover-close
                              :title="tt('AI Clipboard Text Recognition')"
                              @click="addByRecognizingClipboardText"
                              v-if="isTransactionFromAITextRecognitionEnabled()">
                    <template #media>
                        <f7-icon f7="wand_stars"></f7-icon>
                    </template>
                </f7-list-item>
                <f7-list-item key="AIImageRecognition" link="#" no-chevron popover-close
                              :title="tt('AI Image Recognition')"
                              @click="addByRecognizingReceiptImage"
                              v-if="isTransactionFromAIImageRecognitionEnabled()">
                    <template #media>
                        <f7-icon f7="wand_stars"></f7-icon>
                    </template>
                </f7-list-item>
                <f7-list-item popover-close :key="template.id" :title="template.name"
                              :link="'/transaction/add?templateId=' + template.id"
                              v-for="template in allTransactionTemplates">
                    <template #media>
                        <f7-icon f7="doc_plaintext"></f7-icon>
                    </template>
                </f7-list-item>
            </f7-list>
        </f7-popover>
    </f7-page>
</template>

<script setup lang="ts">
import OverviewDashboard from './overview/OverviewDashboard.vue';
import LedgerContextNotice from '@/components/mobile/LedgerContextNotice.vue';
import LedgerPendingPanel from '@/components/common/LedgerPendingPanel.vue';
import MonthlyOverviewPanel from '@/components/common/MonthlyOverviewPanel.vue';
import { monthInTimezone, type MonthlyOverviewScope } from '@/lib/monthly_overview.ts';
import { mobileLedgerPendingLocation } from '@/lib/ledger_pending_navigation.ts';
import type { LedgerPendingTarget } from '@/models/ledger_pending.ts';

import { ref, computed, watch } from 'vue';
import type { Router } from 'framework7/types';

import { useI18n } from '@/locales/helpers.ts';
import { useI18nUIComponents, isiOS } from '@/lib/ui/mobile.ts';

import { useSettingsStore } from '@/stores/setting.ts';
import { useAccountsStore } from '@/stores/account.ts';
import { useTransactionCategoriesStore } from '@/stores/transactionCategory.ts';
import { useTransactionTemplatesStore } from '@/stores/transactionTemplate.ts';
import { useOverviewStore } from '@/stores/overview.ts';
import { useLedgersStore } from '@/stores/ledger.ts';
import { DefaultLedgerId, LedgerMemberRole, type LedgerOverview } from '@/models/ledger.ts';
import { useUserStore } from '@/stores/user.ts';
import { canWriteMobileLedger, mobileLedgerRole } from '@/lib/mobile_ledger.ts';
import services from '@/lib/services.ts';

import type { PartialRecord } from '@/core/base.ts';
import {
    type MobileOverviewLayout,
    OverviewWidgetDataRequirement,
    MobileOverviewWidgetNavigationType
} from '@/core/overview_layout.ts';
import { TemplateType } from '@/core/template.ts';
import { AccountCategory } from '@/core/account.ts';
import { MOBILE_OVERVIEW_WIDGET_DEFINITIONS, DEFAULT_MOBILE_OVERVIEW_LAYOUT } from '@/consts/overview_layout.ts';

import { TransactionTemplate } from '@/models/transaction_template.ts';
import type { Account } from '@/models/account.ts';

import { isFunction } from '@/lib/common.ts';
import { parseBigDecimal } from '@/lib/numeral.ts';
import {
    getOverviewDataRequirements,
    getOverviewTransactionOverviewMonths,
    getOverviewUnreconciledTransactionAccountIds,
    getOverviewRecentTransactionsQueries,
    getOverviewAssetTrendMonths,
    getOverviewCalendarHeatmapMonths,
    getOverviewTransactionCategoryStatisticDateTypes,
    parseMobileOverviewLayout
} from '@/lib/overview_layout.ts';
import { getTransactionStateIdentity, isUserLogined, isUserUnlocked } from '@/lib/userstate.ts';
import { getShareCacheImageBlob } from '@/lib/cache.ts';
import {
    isTransactionFromAITextRecognitionEnabled,
    isTransactionFromAIImageRecognitionEnabled
} from '@/lib/server_settings.ts';
import logger from '@/lib/logger.ts';

const props = defineProps<{
    f7router: Router.Router;
}>();

const { tt, formatAmountToLocalizedNumeralsWithCurrency } = useI18n();
const { showToast } = useI18nUIComponents();

const settingsStore = useSettingsStore();
const showAmountInHomePage = computed<boolean>(() => settingsStore.appSettings.showAmountInHomePage);

const accountsStore = useAccountsStore();
const transactionCategoriesStore = useTransactionCategoriesStore();
const transactionTemplatesStore = useTransactionTemplatesStore();
const overviewStore = useOverviewStore();
const ledgersStore = useLedgersStore();
const userStore = useUserStore();
const pendingPageActive = ref(true);
const monthlyOverviewTimezone = ref<string>(Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC');
const monthlyOverviewMonth = ref<string>(monthInTimezone(new Date(), monthlyOverviewTimezone.value));
const monthlyOverviewScope = computed<MonthlyOverviewScope>(() => ({
    identity: getTransactionStateIdentity() ?? '',
    sessionEpoch: 0,
    ledgerId: ledgersStore.selectedLedgerId,
    month: monthlyOverviewMonth.value,
    timezone: monthlyOverviewTimezone.value,
    hidden: 'include',
    mode: navigator.onLine === false ? 'offline' : 'online'
}));
function openMonthlyReport(): void { props.f7router.navigate('/statistic/transaction'); }
function openMonthlyBudget(): void { props.f7router.navigate('/settings/budget'); }
function openPendingTarget(target: LedgerPendingTarget): void { props.f7router.navigate(mobileLedgerPendingLocation(target)); }
const isDefaultLedger = computed(() => ledgersStore.selectedLedgerId === DefaultLedgerId);
const ledgerRole = ref<number | null>(null);
const permissionLedgerId = ref<string | null>(null);
const permissionUsername = ref<string | undefined>(undefined);
const permissionError = ref('');
const canWriteLedger = computed(() => permissionLedgerId.value === ledgersStore.selectedLedgerId && permissionUsername.value === userStore.currentUserBasicInfo?.username && canWriteMobileLedger(ledgerRole.value));
const aiSourceAvailable = computed(() => isTransactionFromAITextRecognitionEnabled() || isTransactionFromAIImageRecognitionEnabled());
const scopedOverview = ref<LedgerOverview | null>(null);
const loadError = ref('');
let loadGeneration = 0;

const loading = ref<boolean>(true);
const showTransactionTemplatePopover = ref<boolean>(false);
const mobileWalletAccounts = computed<Account[]>(() => accountsStore.accountListLedgerId === ledgersStore.selectedLedgerId ? accountsStore.allVisiblePlainAccounts.slice(0, 4) : []);

const layout = computed<MobileOverviewLayout>(() => {
    try {
        return parseMobileOverviewLayout(settingsStore.appSettings.mobileOverviewPageLayout);
    } catch (error) {
        logger.warn('failed to parse mobile overview page layout, fallback to default layout', error);
        return DEFAULT_MOBILE_OVERVIEW_LAYOUT;
    }
});

function formatMobileWalletBalance(balance: string, currency: string): string {
    return formatAmountToLocalizedNumeralsWithCurrency(parseBigDecimal(balance), currency);
}

function mobileWalletCardClass(category: number): string {
    if (category === AccountCategory.CreditCard.type || category === AccountCategory.DebtAccount.type) return 'mobile-wallet-card-coral';
    if (category === AccountCategory.VirtualAccount.type) return 'mobile-wallet-card-cyan';
    if (category === AccountCategory.Cash.type) return 'mobile-wallet-card-amber';
    if (category === AccountCategory.InvestmentAccount.type) return 'mobile-wallet-card-ink';
    return 'mobile-wallet-card-violet';
}

function mobileWalletCategoryLabel(category: number): string {
    if (category === AccountCategory.Cash.type) return '现金';
    if (category === AccountCategory.CreditCard.type) return '信用账户';
    if (category === AccountCategory.VirtualAccount.type) return '数字余额';
    if (category === AccountCategory.DebtAccount.type) return '负债账户';
    if (category === AccountCategory.InvestmentAccount.type) return '投资账户';
    return '储蓄账户';
}

const allTransactionTemplates = computed<TransactionTemplate[]>(() => {
    const allTemplates = transactionTemplatesStore.allVisibleTemplates;
    return allTemplates[TemplateType.Normal.type] || [];
});

const hasTransactionAddMenuItems = computed<boolean>(() => isTransactionFromAITextRecognitionEnabled() || isTransactionFromAIImageRecognitionEnabled() || (allTransactionTemplates.value && allTransactionTemplates.value.length > 0));

function openTransactionTemplatePopover(): void {
    if (aiSourceAvailable.value || (isDefaultLedger.value && hasTransactionAddMenuItems.value)) {
        showTransactionTemplatePopover.value = true;
    }
}

async function init(force = false): Promise<void> {
    if (!isUserLogined() || !isUserUnlocked()) return;
    const generation = ++loadGeneration;
    const ledgerId = ledgersStore.selectedLedgerId;
    const username = userStore.currentUserBasicInfo?.username;
    ledgerRole.value = null;
    permissionLedgerId.value = null;
    permissionError.value = '';
    loading.value = true;
    loadError.value = '';
    const permission = (ledgerId === DefaultLedgerId ? Promise.resolve(LedgerMemberRole.Owner) : services.listLedgerMembers({ ledgerId }).then(response => {
        if (!response.data?.success || !Array.isArray(response.data.result)) throw new Error('无法读取当前账本权限');
        return mobileLedgerRole(ledgerId, response.data.result);
    })).then(role => {
        if (generation !== loadGeneration || ledgerId !== ledgersStore.selectedLedgerId || username !== userStore.currentUserBasicInfo?.username) return;
        ledgerRole.value = role;
        permissionLedgerId.value = ledgerId;
        permissionUsername.value = username;
    }).catch(() => {
        if (generation === loadGeneration && ledgerId === ledgersStore.selectedLedgerId && username === userStore.currentUserBasicInfo?.username) permissionError.value = '无法读取当前账本权限，记账入口已禁用。';
    });
    try {
        await permission;
        const shareBlob = await getShareCacheImageBlob();
        if (ledgerId !== DefaultLedgerId) {
            const [, overview] = await Promise.all([
                accountsStore.loadAllAccounts({ force: false, ledgerId }),
                ledgersStore.loadOverview(ledgerId)
            ]);
            if (generation !== loadGeneration || ledgerId !== ledgersStore.selectedLedgerId) return;
            scopedOverview.value = overview;
        } else {
            await Promise.all([
                accountsStore.loadAllAccounts({ force: false, ledgerId }),
                transactionCategoriesStore.loadAllCategories({ force: false, ledgerId }),
                transactionTemplatesStore.loadAllTemplates({ templateType: TemplateType.Normal.type, force: false }),
                ...reloadOverviewData(force)
            ]);
            if (generation !== loadGeneration || ledgerId !== ledgersStore.selectedLedgerId) return;
        }
        if (shareBlob instanceof Blob) addByRecognizingSharedImage(shareBlob);
    } catch (error) {
        if (generation !== loadGeneration || ledgerId !== ledgersStore.selectedLedgerId) return;
        const failure = error as { message?: string; processed?: boolean; isUpToDate?: boolean; stale?: boolean };
        if (!failure.isUpToDate && !failure.stale) {
            loadError.value = failure.message || '无法加载当前账本，请重试。';
            if (!failure.processed) showToast(loadError.value);
        }
    } finally {
        if (generation === loadGeneration) loading.value = false;
    }
}

function reload(done?: () => void): void {
    void init(!!done).finally(() => done?.());
}

watch(() => [ledgersStore.selectedLedgerId, userStore.currentUserBasicInfo?.username], () => {
    scopedOverview.value = null;
    showTransactionTemplatePopover.value = false;
    void init();
}, { flush: 'sync' });

/** Every mobile AI entry hands off to the durable source page of the current
 * ledger; nothing is recognized here and no financial field travels in a URL. */
function openAISourcePage(entry: { sourceText?: string; sourceImage?: File; sourceType?: 1 | 2 }): void {
    if (!canWriteLedger.value) return;
    props.f7router.navigate('/ai/source', { props: entry });
}

function reloadOverviewData(force: boolean): Promise<unknown>[] {
    const requirements: PartialRecord<OverviewWidgetDataRequirement, boolean> = getOverviewDataRequirements(layout.value, MOBILE_OVERVIEW_WIDGET_DEFINITIONS);
    const promises: Promise<unknown>[] = [];

    if (requirements[OverviewWidgetDataRequirement.TransactionOverview]) {
        promises.push(overviewStore.loadTransactionOverview({
            force: force,
            months: getOverviewTransactionOverviewMonths(layout.value)
        }));
    }

    if (requirements[OverviewWidgetDataRequirement.AccountUnreconciledTransactionCounts]) {
        promises.push(overviewStore.loadTransactionUnreconciledCounts({
            force: force,
            accountIds: getOverviewUnreconciledTransactionAccountIds(layout.value)
        }));
    }

    if (requirements[OverviewWidgetDataRequirement.TransactionCategoryStatistics]) {
        for (const dateType of getOverviewTransactionCategoryStatisticDateTypes(layout.value)) {
            promises.push(overviewStore.loadTransactionCategoryStatistics({
                force: force,
                dateType: dateType
            }));
        }
    }

    if (requirements[OverviewWidgetDataRequirement.AssetTrends]) {
        promises.push(overviewStore.loadTransactionAssetTrends({
            force: force,
            months: getOverviewAssetTrendMonths(layout.value)
        }));
    }

    if (requirements[OverviewWidgetDataRequirement.RecentTransactions]) {
        promises.push(overviewStore.loadRecentTransactions({
            force: force,
            queries: getOverviewRecentTransactionsQueries(layout.value)
        }));
    }

    if (requirements[OverviewWidgetDataRequirement.CurrentMonthTransactions]) {
        promises.push(overviewStore.loadCurrentMonthTransactions({
            force: force
        }));
    }

    if (requirements[OverviewWidgetDataRequirement.DailyTransactionAmounts]) {
        promises.push(overviewStore.loadTransactionDailyAmounts({
            force: force,
            months: getOverviewCalendarHeatmapMonths(layout.value)
        }));
    }

    return promises;
}

function addByRecognizingClipboardText(): void {
    if (!canWriteLedger.value) return;
    const username = userStore.currentUserBasicInfo?.username;
    if (navigator.clipboard && isFunction(navigator.clipboard.readText) && !isiOS()) {
        navigator.clipboard.readText().then(text => {
            if (username !== userStore.currentUserBasicInfo?.username) return;
            openAISourcePage({ sourceText: text && text.trim() ? text.trim() : '' });
        }).catch(error => {
            if (username !== userStore.currentUserBasicInfo?.username) return;
            logger.error('failed to read clipboard', error);
            openAISourcePage({ sourceText: '' });
        });
    } else {
        openAISourcePage({ sourceText: '' });
    }
}

function addByRecognizingReceiptImage(): void {
    openAISourcePage({ sourceType: 2 });
}

function addByRecognizingSharedImage(blob: Blob): void {
    const type = blob.type && blob.type.trim() ? blob.type : 'image/jpeg';
    openAISourcePage({ sourceType: 2, sourceImage: new File([blob], 'shared-receipt.jpg', { type }) });
}

function onNavigate(type: MobileOverviewWidgetNavigationType, path?: string): void {
    if (type === MobileOverviewWidgetNavigationType.Url && path) {
        props.f7router.navigate(path);
    } else if (type === MobileOverviewWidgetNavigationType.AIClipboardTextRecognition) {
        addByRecognizingClipboardText();
    } else if (type === MobileOverviewWidgetNavigationType.AIImageRecognition) {
        addByRecognizingReceiptImage();
    }
}

function onPageAfterIn(): void {
    pendingPageActive.value = true;
    if (!loading.value) {
        reload();
    }
}

init();
</script>

<style>
.mobile-pending-entry { margin: 16px; }
.mobile-ledger-actions { display: grid; grid-template-columns: repeat(2, minmax(0,1fr)); gap: 10px; margin: 12px 16px 4px; padding: 0; }
.mobile-monthly-overview { margin: 12px 16px; }
.mobile-ledger-action { min-height: 48px; justify-content: center; gap: 8px; border: 1px solid var(--f7-list-border-color); border-radius: 14px; background: var(--f7-card-bg-color); font-weight: 700; }
.mobile-onboarding {
    display: grid;
    grid-template-columns: 44px minmax(0, 1fr) auto;
    align-items: center;
    gap: 12px;
    margin: 16px;
    padding: 14px;
    border: 1px solid color-mix(in srgb, var(--f7-theme-color) 24%, transparent);
    border-radius: 18px;
    background: color-mix(in srgb, var(--f7-theme-color) 8%, var(--f7-card-bg-color));
}
.mobile-onboarding-icon { display: grid; place-items: center; width: 44px; height: 44px; border-radius: 14px; color: var(--f7-theme-color); background: color-mix(in srgb, var(--f7-theme-color) 14%, transparent); }
.mobile-onboarding-copy { display: grid; gap: 2px; min-width: 0; }
.mobile-onboarding-copy small { color: var(--f7-theme-color); font-size: 10px; font-weight: 750; letter-spacing: .08em; }
.mobile-onboarding-copy strong { font-size: 15px; }
.mobile-onboarding-copy span { color: var(--f7-text-color-secondary); font-size: 12px; line-height: 1.4; }
.mobile-onboarding-action { min-width: 52px; min-height: 44px; justify-content: center; font-weight: 700; touch-action: manipulation; }

.mobile-wallet { margin: 20px 16px 24px; }
.mobile-section-heading { display: flex; align-items: end; justify-content: space-between; margin-bottom: 12px; }
.mobile-section-heading > div { display: grid; gap: 3px; }
.mobile-section-heading small { color: var(--f7-theme-color); font: 750 10px/1 ui-monospace, monospace; letter-spacing: .14em; }
.mobile-section-heading strong { font-size: 22px; line-height: 1.2; }
.mobile-section-heading > a { min-width: 52px; min-height: 44px; justify-content: flex-end; touch-action: manipulation; }
.mobile-wallet-stack { display: grid; }
.mobile-wallet-card {
    position: relative;
    display: grid;
    min-height: 164px;
    padding: 16px;
    overflow: hidden;
    border: 1px solid rgba(255,255,255,.24);
    border-radius: 20px;
    color: #fff;
    box-shadow: 0 14px 28px rgba(13, 20, 48, .18);
    touch-action: manipulation;
    transition: transform 180ms ease, filter 180ms ease;
}
.mobile-wallet-card + .mobile-wallet-card { margin-top: -104px; }
.mobile-wallet-card::after { content: ""; position: absolute; width: 190px; height: 190px; inset: -120px -60px auto auto; border: 1px solid rgba(255,255,255,.18); border-radius: 50%; pointer-events: none; }
.mobile-wallet-card:active { transform: scale(.985); filter: brightness(.94); }
.mobile-wallet-card-violet { background: linear-gradient(145deg, #6b5ce8, #352c81); }
.mobile-wallet-card-cyan { background: linear-gradient(145deg, #117f8d, #15345d); }
.mobile-wallet-card-coral { background: linear-gradient(145deg, #bd5468, #552a64); }
.mobile-wallet-card-amber { background: linear-gradient(145deg, #b87927, #58352c); }
.mobile-wallet-card-ink { background: linear-gradient(145deg, #29344c, #0b101d); }
.mobile-wallet-card-top, .mobile-wallet-card-bottom { position: relative; z-index: 1; display: flex; justify-content: space-between; }
.mobile-wallet-card-top { align-items: center; align-self: start; font-weight: 750; letter-spacing: .1em; opacity: .82; }
.mobile-wallet-chip { align-self: start; width: 36px; height: 26px; margin-top: 14px; border: 1px solid rgba(79,55,22,.3); border-radius: 7px; background: linear-gradient(135deg, #f4dc9d, #b98737); }
.mobile-wallet-card-bottom { align-items: end; align-self: end; }
.mobile-wallet-card-bottom > span { display: grid; }
.mobile-wallet-card-bottom small { font-size: 10px; opacity: .66; }
.mobile-wallet-card-bottom strong { font-size: 18px; font-variant-numeric: tabular-nums; }
.mobile-wallet-card-bottom b { max-width: 42%; overflow: hidden; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.mobile-wallet-empty { display: grid; grid-template-columns: 44px minmax(0,1fr) 24px; align-items: center; gap: 12px; min-height: 78px; padding: 12px; border: 1px dashed color-mix(in srgb, var(--f7-theme-color) 38%, transparent); border-radius: 18px; background: color-mix(in srgb, var(--f7-theme-color) 6%, var(--f7-card-bg-color)); touch-action: manipulation; }
.mobile-wallet-empty > i:first-child { display: grid; place-items: center; width: 44px; height: 44px; border-radius: 14px; background: color-mix(in srgb, var(--f7-theme-color) 13%, transparent); }
.mobile-wallet-empty > span { display: grid; gap: 2px; }
.mobile-wallet-empty small { color: var(--f7-text-color-secondary); font-size: 11px; }
.mobile-wallet-skeleton { display: grid; gap: 10px; }
.mobile-wallet-skeleton span { display: block; height: 164px; border-radius: 20px; background: var(--f7-skeleton-color); }

.home-summary-card {
    background-color: var(--f7-color-yellow);
}

.home-summary-card .home-summary-month {
    font-size: 1.3em;
}

.home-summary-card .month-expense {
    font-size: 1.5em;
}

.home-summary-card .home-summary-misc {
    opacity: 0.6;
}

.home-summary-misc > span {
    margin-inline-end: 4px;
}

.home-summary-misc > span:last-child {
    margin-inline-end: 0;
}

.dark .home-summary-card {
    background-color: var(--f7-theme-color);
}

.dark .home-summary-card a {
    color: var(--f7-text-color);
    opacity: 0.6;
}

.overview-transaction-list .item-title > div {
    overflow: hidden;
    text-overflow: ellipsis;
}

.overview-transaction-list .item-after {
    max-width: 100%;
}

.overview-transaction-list .overview-transaction-footer {
    padding-top: 6px;
    font-size: var(--ebk-large-footer-font-size);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.overview-transaction-list .overview-transaction-footer > span {
    margin-inline-end: 4px;
}

.overview-transaction-list .overview-transaction-amount {
    max-width: 100%;
}

.overview-transaction-list .overview-transaction-amount > div {
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
}

.tabbar.main-tabbar .link i + span.tabbar-label {
    margin-top: var(--ebk-icon-text-margin);
}

.tabbar.main-tabbar .link i.ebk-tarbar-big-icon {
    font-size: var(--ebk-big-icon-button-size);
    width: var(--ebk-big-icon-button-size);
    height: var(--ebk-big-icon-button-size);
    line-height: var(--ebk-big-icon-button-size);
}

.template-popover-menu .popover-inner {
    max-height: 400px;
    overflow-y: auto;
}
</style>
