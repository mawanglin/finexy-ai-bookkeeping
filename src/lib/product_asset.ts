import { ProductAssetStatus, type ProductAssetInfoResponse } from '@/models/product_asset.ts';

export type AssetLifeUnit = 'year' | 'month' | 'day';

const MAX_MINOR_UNITS = 9_999_999_999_999;

/** Exact decimal text to minor units (two decimals). Returns null for anything that is not a plain non-negative amount. */
export function assetAmountToMinor(text: string): number | null {
    const value = text.trim();
    const match = /^(\d{1,13})(?:\.(\d{1,2}))?$/.exec(value);
    if (!match) return null;
    const minor = Number(match[1]) * 100 + Number((match[2] ?? '').padEnd(2, '0') || '0');
    return Number.isSafeInteger(minor) && minor <= MAX_MINOR_UNITS ? minor : null;
}

export function assetLifeDays(value: number, unit: AssetLifeUnit): number {
    const amount = Math.max(1, Math.round(value || 1));
    return unit === 'year' ? amount * 365 : unit === 'month' ? amount * 30 : amount;
}

export function assetLifeFromDays(days: number): { value: number; unit: AssetLifeUnit } {
    if (days >= 365 && days % 365 === 0) return { value: days / 365, unit: 'year' };
    if (days >= 30 && days % 30 === 0) return { value: days / 30, unit: 'month' };
    return { value: Math.max(1, days), unit: 'day' };
}

export function assetSummary(assets: readonly ProductAssetInfoResponse[]) {
    const purchaseAmount = assets.reduce((sum, item) => sum + item.purchaseAmount, 0);
    const bookValue = assets.reduce((sum, item) => sum + item.valuation.bookValue, 0);
    const depreciation = assets.reduce((sum, item) => sum + item.valuation.accumulatedDepreciation, 0);
    return {
        count: assets.length,
        activeCount: assets.filter(item => item.status === ProductAssetStatus.Active).length,
        purchaseAmount, bookValue, depreciation
    };
}
