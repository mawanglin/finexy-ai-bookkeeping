import { describe, expect, it } from 'vitest';
import { ProductAssetStatus, type ProductAssetInfoResponse } from '@/models/product_asset.ts';
import { assetAmountToMinor, assetLifeDays, assetLifeFromDays, assetSummary } from '../product_asset.ts';

function asset(patch: Partial<ProductAssetInfoResponse> & { book?: number; dep?: number }): ProductAssetInfoResponse {
    return {
        id: '1', category: 1, status: ProductAssetStatus.Active, name: 'x', brand: '', model: '', purchaseAmount: 0, purchaseTime: 0, utcOffset: 0,
        usefulLifeDays: 365, residualAmount: 0, comment: '',
        valuation: { heldDays: 0, dailyDepreciation: 0, accumulatedDepreciation: patch.dep ?? 0, bookValue: patch.book ?? 0, averageDailyCost: 0 }, ...patch
    };
}

describe('assetAmountToMinor', () => {
    it.each([['0', 0], ['12', 1200], ['12.3', 1230], ['12.30', 1230], ['0.05', 5], [' 8.99 ', 899], ['1.005'.slice(0, 4), 100]])('%s -> %s', (text, expected) => {
        expect(assetAmountToMinor(text)).toBe(expected);
    });

    it('is exact where floating point multiplication is not', () => {
        expect(assetAmountToMinor('1.15')).toBe(115);
        expect(assetAmountToMinor('4.35')).toBe(435);
        expect(assetAmountToMinor('19.99')).toBe(1999);
    });

    it.each(['', '-1', '1.234', '1e3', 'abc', '1,000', '.5', '12.', '99999999999999'])('rejects %s', text => {
        expect(assetAmountToMinor(text)).toBeNull();
    });
});

describe('asset life conversion', () => {
    it('converts units to days with the same factors as the desktop form', () => {
        expect(assetLifeDays(3, 'year')).toBe(1095);
        expect(assetLifeDays(6, 'month')).toBe(180);
        expect(assetLifeDays(10, 'day')).toBe(10);
        expect(assetLifeDays(0, 'day')).toBe(1);
    });

    it('turns stored days back into the most natural unit', () => {
        expect(assetLifeFromDays(1095)).toEqual({ value: 3, unit: 'year' });
        expect(assetLifeFromDays(180)).toEqual({ value: 6, unit: 'month' });
        expect(assetLifeFromDays(45)).toEqual({ value: 45, unit: 'day' });
    });
});

describe('assetSummary', () => {
    it('adds purchase, book value and depreciation and counts active items', () => {
        const result = assetSummary([
            asset({ purchaseAmount: 10000, book: 6000, dep: 4000 }),
            asset({ purchaseAmount: 5000, book: 0, dep: 5000, status: ProductAssetStatus.Sold })
        ]);
        expect(result).toEqual({ count: 2, activeCount: 1, purchaseAmount: 15000, bookValue: 6000, depreciation: 9000 });
    });

    it('is zero for an empty list', () => {
        expect(assetSummary([])).toEqual({ count: 0, activeCount: 0, purchaseAmount: 0, bookValue: 0, depreciation: 0 });
    });
});
