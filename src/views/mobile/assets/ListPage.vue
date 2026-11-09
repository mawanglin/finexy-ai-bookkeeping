<template>
    <f7-page ptr @ptr:refresh="refresh">
        <f7-navbar title="产品资产" back-link="返回">
            <f7-nav-right><f7-link icon-f7="plus" aria-label="新增产品资产" :class="{ disabled: busy }" @click="openEditor()"></f7-link></f7-nav-right>
        </f7-navbar>

        <f7-block v-if="error" class="text-color-red" role="alert">{{ error }} <f7-link :class="{ disabled: busy }" @click="load(true)">重试</f7-link></f7-block>
        <f7-block v-if="!error && assetsStore.loaded" class="asset-note">资产记录跟随你的账号，与当前账本无关；金额按默认币种 {{ currency }} 显示。</f7-block>

        <f7-card v-if="assetsStore.loaded" class="asset-summary" role="group" aria-label="资产汇总">
            <f7-card-content>
                <p class="asset-summary-title">当前账面价值</p>
                <p class="asset-summary-value">{{ money(summary.bookValue) }}</p>
                <p class="text-color-gray">共 {{ summary.count }} 件 · {{ summary.activeCount }} 件使用中</p>
                <div class="asset-summary-grid">
                    <span>累计购入<b>{{ money(summary.purchaseAmount) }}</b></span>
                    <span>累计折旧<b>{{ money(summary.depreciation) }}</b></span>
                </div>
            </f7-card-content>
        </f7-card>

        <f7-segmented strong v-if="assetsStore.loaded && assetsStore.assets.length" class="asset-filter">
            <f7-button v-for="item in filters" :key="item.value" :active="statusFilter === item.value" @click="statusFilter = item.value">{{ item.label }}</f7-button>
        </f7-segmented>

        <f7-list v-if="visibleAssets.length" strong inset dividers media-list>
            <f7-list-item v-for="asset in visibleAssets" :key="asset.id" link="#" :title="asset.name"
                          :subtitle="[categoryLabel(asset.category), asset.brand, asset.model].filter(Boolean).join(' · ')"
                          :after="money(asset.valuation.bookValue)" :text="`${statusLabel(asset.status)} · 购入 ${money(asset.purchaseAmount)}`"
                          :aria-label="`查看产品资产：${asset.name}`" @click="openDetail(asset)"></f7-list-item>
        </f7-list>
        <f7-block v-else-if="!error && assetsStore.loaded" class="text-align-center text-color-gray">
            {{ assetsStore.assets.length ? '没有符合筛选条件的资产。' : '还没有产品资产。点右上角“+”记录一件，系统会按使用寿命估算折旧。' }}
        </f7-block>
        <f7-block v-else-if="!error" class="text-align-center text-color-gray" role="status">正在读取产品资产…</f7-block>

        <f7-sheet class="asset-detail-sheet" style="height: 86%" v-model:opened="detailOpen" swipe-to-close backdrop>
            <f7-page-content v-if="detail">
                <f7-block-title>{{ detail.name }}</f7-block-title>
                <f7-list strong inset dividers>
                    <f7-list-item title="状态" :after="statusLabel(detail.status)"></f7-list-item>
                    <f7-list-item title="类别" :after="categoryLabel(detail.category)"></f7-list-item>
                    <f7-list-item title="品牌 / 型号" :after="[detail.brand, detail.model].filter(Boolean).join(' / ') || '未填写'"></f7-list-item>
                    <f7-list-item title="购入金额" :after="money(detail.purchaseAmount)"></f7-list-item>
                    <f7-list-item title="购入时间" :after="dateText(detail.purchaseTime)"></f7-list-item>
                    <f7-list-item title="预计使用寿命" :after="`${detail.usefulLifeDays} 天`"></f7-list-item>
                    <f7-list-item title="残值" :after="money(detail.residualAmount)"></f7-list-item>
                    <f7-list-item title="已使用" :after="`${detail.valuation.heldDays} 天`"></f7-list-item>
                    <f7-list-item title="每日折旧" :after="money(detail.valuation.dailyDepreciation)"></f7-list-item>
                    <f7-list-item title="累计折旧" :after="money(detail.valuation.accumulatedDepreciation)"></f7-list-item>
                    <f7-list-item title="账面价值" :after="money(detail.valuation.bookValue)"></f7-list-item>
                    <f7-list-item v-if="detail.valuation.marketValue !== undefined" title="参考估值" :after="money(detail.valuation.marketValue)"></f7-list-item>
                    <f7-list-item v-if="detail.soldAmount !== undefined" title="售出金额" :after="money(detail.soldAmount)"></f7-list-item>
                    <f7-list-item v-if="detail.soldTime" title="售出时间" :after="dateText(detail.soldTime)"></f7-list-item>
                    <f7-list-item title="备注" :after="detail.comment || '无'"></f7-list-item>
                </f7-list>
                <f7-block class="asset-actions">
                    <f7-button fill :class="{ disabled: busy }" @click="openEditor(detail)">编辑</f7-button>
                    <f7-button outline v-if="detail.status === ProductAssetStatus.Active" :class="{ disabled: busy }" @click="openSell(detail)">登记售出</f7-button>
                    <f7-button outline color="red" :class="{ disabled: busy }" @click="confirmRemove(detail)">删除</f7-button>
                </f7-block>
                <f7-block class="text-color-gray">登记售出或删除只更新这条资产记录，不会自动新增或删除流水。</f7-block>
            </f7-page-content>
        </f7-sheet>

        <f7-sheet class="asset-edit-sheet" style="height: 92%" v-model:opened="editorOpen" swipe-to-close backdrop>
            <f7-page-content>
                <f7-block-title>{{ editing ? '编辑产品资产' : '新增产品资产' }}</f7-block-title>
                <f7-list strong inset dividers>
                    <f7-list-input label="名称" type="text" maxlength="64" placeholder="例如：iPhone 15" v-model:value="form.name"></f7-list-input>
                    <f7-list-input label="类别" type="select" v-model:value="form.category">
                        <option v-for="item in categoryOptions" :key="item.value" :value="String(item.value)">{{ item.label }}</option>
                    </f7-list-input>
                    <f7-list-input label="品牌" type="text" maxlength="64" v-model:value="form.brand"></f7-list-input>
                    <f7-list-input label="型号" type="text" maxlength="64" v-model:value="form.model"></f7-list-input>
                    <f7-list-input :label="`购入金额（${currency}）`" type="text" inputmode="decimal" placeholder="0.00" v-model:value="form.amount"></f7-list-input>
                    <f7-list-input label="购入日期" type="date" v-model:value="form.date"></f7-list-input>
                    <f7-list-input label="预计使用寿命" type="number" min="1" step="1" v-model:value="form.life"></f7-list-input>
                    <f7-list-input label="寿命单位" type="select" v-model:value="form.unit">
                        <option value="year">年</option><option value="month">月</option><option value="day">天</option>
                    </f7-list-input>
                    <f7-list-input :label="`残值（${currency}）`" type="text" inputmode="decimal" placeholder="0.00" v-model:value="form.residual"></f7-list-input>
                    <f7-list-input :label="`手动估值（${currency}，可留空）`" type="text" inputmode="decimal" placeholder="留空则按折旧估算" v-model:value="form.market"></f7-list-input>
                    <f7-list-input label="备注" type="text" maxlength="255" v-model:value="form.comment"></f7-list-input>
                </f7-list>
                <f7-block v-if="formProblem" class="text-color-red" role="alert">{{ formProblem }}</f7-block>
                <f7-block><f7-button fill large :class="{ disabled: busy || !!formProblem }" @click="saveAsset">{{ busy ? '正在保存…' : '保存' }}</f7-button></f7-block>
            </f7-page-content>
        </f7-sheet>

        <f7-sheet class="asset-sell-sheet" v-model:opened="sellOpen" swipe-to-close backdrop>
            <f7-page-content>
                <f7-block-title>登记售出：{{ sellTarget?.name }}</f7-block-title>
                <f7-list strong inset dividers>
                    <f7-list-input :label="`售出金额（${currency}）`" type="text" inputmode="decimal" placeholder="0.00" v-model:value="sellAmount"></f7-list-input>
                    <f7-list-input label="售出日期" type="date" v-model:value="sellDate"></f7-list-input>
                </f7-list>
                <f7-block v-if="sellProblem" class="text-color-red" role="alert">{{ sellProblem }}</f7-block>
                <f7-block><f7-button fill large :class="{ disabled: busy || !!sellProblem }" @click="submitSell">确认售出</f7-button></f7-block>
            </f7-page-content>
        </f7-sheet>
    </f7-page>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';

import { useI18n } from '@/locales/helpers.ts';
import { useUserStore } from '@/stores/user.ts';
import { useProductAssetsStore } from '@/stores/productAsset.ts';
import { ProductAssetCategory, ProductAssetStatus, type ProductAssetInfoResponse } from '@/models/product_asset.ts';
import { parseBigDecimal } from '@/lib/numeral.ts';
import { assetAmountToMinor, assetLifeDays, assetLifeFromDays, assetSummary, type AssetLifeUnit } from '@/lib/product_asset.ts';
import { useI18nUIComponents } from '@/lib/ui/mobile.ts';

const { formatAmountToLocalizedNumeralsWithCurrency } = useI18n();
const { showToast, showConfirm } = useI18nUIComponents();
const userStore = useUserStore();
const assetsStore = useProductAssetsStore();

const currency = computed(() => userStore.currentUserDefaultCurrency || 'CNY');
const busy = ref(false);
const error = ref('');
const statusFilter = ref<'all' | 'active' | 'closed'>('all');
const filters = [{ label: '全部', value: 'all' as const }, { label: '使用中', value: 'active' as const }, { label: '已处置', value: 'closed' as const }];
const detailOpen = ref(false);
const detail = ref<ProductAssetInfoResponse | null>(null);
const editorOpen = ref(false);
const editing = ref<ProductAssetInfoResponse | null>(null);
const sellOpen = ref(false);
const sellTarget = ref<ProductAssetInfoResponse | null>(null);
const sellAmount = ref('');
const sellDate = ref('');

const categoryOptions = [
    { label: '其他产品', value: ProductAssetCategory.Other },
    { label: '数码产品 · 手机', value: ProductAssetCategory.Phone },
    { label: '数码产品 · 电脑', value: ProductAssetCategory.Computer },
    { label: '数码产品 · 平板', value: ProductAssetCategory.Tablet },
    { label: '数码产品 · 相机', value: ProductAssetCategory.Camera },
    { label: '数码产品 · 游戏设备', value: ProductAssetCategory.GameConsole },
    { label: '家用电器', value: ProductAssetCategory.Appliance }
];
const form = reactive({ name: '', category: String(ProductAssetCategory.Other), brand: '', model: '', amount: '', date: '', life: '3', unit: 'year' as AssetLifeUnit, residual: '0', market: '', comment: '' });

const summary = computed(() => assetSummary(assetsStore.assets));
const visibleAssets = computed(() => assetsStore.assets.filter(item => statusFilter.value === 'all' ||
    (statusFilter.value === 'active' ? item.status === ProductAssetStatus.Active : item.status !== ProductAssetStatus.Active)));

const formProblem = computed(() => {
    if (!form.name.trim()) return '请填写名称';
    const amount = assetAmountToMinor(form.amount);
    if (amount === null) return '购入金额格式不正确，最多两位小数';
    if (!form.date) return '请选择购入日期';
    const life = Number(form.life);
    if (!Number.isInteger(life) || life < 1) return '使用寿命需为不小于 1 的整数';
    const residual = assetAmountToMinor(form.residual || '0');
    if (residual === null) return '残值格式不正确，最多两位小数';
    if (residual > amount) return '残值不能高于购入金额';
    if (form.market.trim() && assetAmountToMinor(form.market) === null) return '手动估值格式不正确，最多两位小数';
    return '';
});
const sellProblem = computed(() => {
    if (assetAmountToMinor(sellAmount.value) === null) return '售出金额格式不正确，最多两位小数';
    if (!sellDate.value) return '请选择售出日期';
    return '';
});

function money(value: number): string {
    return formatAmountToLocalizedNumeralsWithCurrency(parseBigDecimal(Math.round(Number.isFinite(value) ? value : 0)), currency.value);
}
function dateText(seconds: number): string { return new Date(seconds * 1000).toLocaleDateString(); }
function dateInput(seconds: number): string {
    const date = new Date(seconds * 1000);
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}
function statusLabel(status: number): string { return status === ProductAssetStatus.Active ? '使用中' : status === ProductAssetStatus.Sold ? '已售出' : '已处置'; }
function categoryLabel(category: number): string { return categoryOptions.find(item => item.value === category)?.label ?? '其他产品'; }
function messageOf(reason: unknown): string {
    const failure = reason as { message?: string; error?: { errorMessage?: string } };
    return failure?.error?.errorMessage || failure?.message || (reason instanceof Error ? reason.message : '操作失败，请稍后重试');
}
function noon(date: string): number { return Math.floor(new Date(`${date}T12:00:00`).getTime() / 1000); }

async function load(force: boolean): Promise<void> {
    busy.value = true; error.value = '';
    try { await assetsStore.loadAll(force); }
    catch (reason) { error.value = `读取产品资产失败：${messageOf(reason)}`; }
    finally { busy.value = false; }
}
function refresh(done: () => void): void { void load(true).finally(done); }

function openDetail(asset: ProductAssetInfoResponse): void { detail.value = asset; detailOpen.value = true; }
function openEditor(asset?: ProductAssetInfoResponse | null): void {
    editing.value = asset ?? null;
    const life = assetLifeFromDays(asset?.usefulLifeDays ?? 1095);
    Object.assign(form, {
        name: asset?.name ?? '', category: String(asset?.category ?? ProductAssetCategory.Other), brand: asset?.brand ?? '', model: asset?.model ?? '',
        amount: asset ? (asset.purchaseAmount / 100).toFixed(2) : '', date: asset ? dateInput(asset.purchaseTime) : dateInput(Math.floor(Date.now() / 1000)),
        life: String(life.value), unit: life.unit, residual: asset ? (asset.residualAmount / 100).toFixed(2) : '0',
        market: asset?.manualMarketValue !== undefined ? (asset.manualMarketValue / 100).toFixed(2) : '', comment: asset?.comment ?? ''
    });
    detailOpen.value = false; editorOpen.value = true;
}
async function saveAsset(): Promise<void> {
    if (busy.value || formProblem.value) return;
    const purchaseTime = noon(form.date);
    const common = {
        sourceTransactionId: editing.value?.sourceTransactionId || '0', category: Number(form.category), name: form.name.trim(), brand: form.brand.trim(), model: form.model.trim(),
        purchaseAmount: assetAmountToMinor(form.amount)!, purchaseTime, utcOffset: -new Date(purchaseTime * 1000).getTimezoneOffset(), comment: form.comment.trim()
    };
    const usefulLifeDays = assetLifeDays(Number(form.life), form.unit);
    const residualAmount = assetAmountToMinor(form.residual || '0')!;
    const market = form.market.trim() ? assetAmountToMinor(form.market)! : undefined;
    busy.value = true;
    try {
        if (editing.value) {
            await assetsStore.modify({ ...common, id: editing.value.id, usefulLifeDays, residualAmount, manualMarketValue: market, clearManualMarketValue: market === undefined && editing.value.manualMarketValue !== undefined });
            showToast('产品资产已保存');
        } else {
            await assetsStore.create({ ...common, usefulLifeDays, residualAmount, manualMarketValue: market });
            showToast('产品资产已创建');
        }
        editorOpen.value = false;
        await assetsStore.loadAll(true);
    } catch (reason) { showToast(messageOf(reason)); }
    finally { busy.value = false; }
}
function openSell(asset: ProductAssetInfoResponse): void {
    sellTarget.value = asset; sellAmount.value = ''; sellDate.value = dateInput(Math.floor(Date.now() / 1000));
    detailOpen.value = false; sellOpen.value = true;
}
async function submitSell(): Promise<void> {
    if (busy.value || sellProblem.value || !sellTarget.value) return;
    busy.value = true;
    try {
        await assetsStore.sell({ id: sellTarget.value.id, saleTransactionId: '0', soldAmount: assetAmountToMinor(sellAmount.value)!, soldTime: noon(sellDate.value) });
        sellOpen.value = false; showToast('已登记售出');
        await assetsStore.loadAll(true);
    } catch (reason) { showToast(messageOf(reason)); }
    finally { busy.value = false; }
}
function confirmRemove(asset: ProductAssetInfoResponse): void {
    showConfirm(`永久删除「${asset.name}」？只删除这条资产记录，已有流水不受影响。`, () => {
        busy.value = true;
        assetsStore.remove(asset.id).then(() => { detailOpen.value = false; showToast('产品资产已删除'); })
            .catch(reason => showToast(messageOf(reason))).finally(() => { busy.value = false; });
    });
}

onMounted(() => { void load(false); });
</script>

<style scoped>
.asset-note { margin-bottom: 0; font-size: 13px; color: var(--f7-text-color); opacity: .7; }
.asset-summary-title { margin: 0; font-size: 13px; opacity: .7; }
.asset-summary-value { margin: 4px 0; font-size: 30px; font-weight: 700; }
.asset-summary-grid { display: flex; gap: 16px; margin-top: 12px; }
.asset-summary-grid span { display: flex; flex-direction: column; flex: 1; font-size: 12px; opacity: .85; }
.asset-summary-grid b { font-size: 15px; margin-top: 2px; }
.asset-filter { margin: 12px 16px; }
.asset-actions { display: grid; gap: 10px; }
</style>
