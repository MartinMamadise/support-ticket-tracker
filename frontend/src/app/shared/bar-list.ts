import { Component, computed, input } from '@angular/core';

export interface BarRow {
  label: string;
  value: number;
}

/**
 * Horizontal bars for one series (a magnitude per category). One hue, thin bars from a common baseline, the value at
 * each tip in text colour, and a title tooltip. Values are all visible, so the chart is its own table.
 */
@Component({
  selector: 'app-bar-list',
  template: `
    <ul class="bar-list" [attr.aria-label]="label()">
      @for (row of rows(); track row.label) {
        <li class="bar-row" [attr.title]="row.label + ': ' + row.value">
          <span class="bar-label">{{ row.label }}</span>
          <span class="bar-track">
            <span class="bar-fill" [style.width.%]="percent(row.value)"></span>
          </span>
          <span class="bar-value">{{ row.value }}</span>
        </li>
      }
    </ul>
  `,
})
export class BarList {
  readonly rows = input.required<BarRow[]>();
  readonly label = input('');

  private readonly max = computed(() => Math.max(1, ...this.rows().map((r) => r.value)));

  protected percent(value: number): number {
    // A tiny visible stub for non-zero values; zero stays empty.
    return value === 0 ? 0 : Math.max(2, (value / this.max()) * 100);
  }
}
