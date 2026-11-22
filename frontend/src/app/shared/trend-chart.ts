import { Component, computed, input, signal } from '@angular/core';

export interface TrendPoint {
  date: string;
  created: number;
  resolved: number;
}

const WIDTH = 640;
const HEIGHT = 200;
const PAD = { top: 12, right: 44, bottom: 24, left: 32 };

/**
 * Created vs resolved tickets per day: two 2px lines on one axis, legend plus end labels, hairline grid, a
 * crosshair tooltip on hover/focus, and a table view. Data comes from the dashboard API only.
 */
@Component({
  selector: 'app-trend-chart',
  template: `
    <div class="chart-head">
      <div class="legend" aria-hidden="true">
        <span class="legend-item"><span class="swatch swatch-1"></span>Created</span>
        <span class="legend-item"><span class="swatch swatch-2"></span>Resolved</span>
      </div>
      <button
        type="button"
        class="btn btn-link btn-sm"
        (click)="showTable.set(!showTable())"
        [attr.aria-pressed]="showTable()"
      >
        {{ showTable() ? 'Show chart' : 'Show table' }}
      </button>
    </div>
    @if (showTable()) {
      <div class="table-wrap">
        <table class="compact">
          <thead>
            <tr>
              <th scope="col">Date (UTC)</th>
              <th scope="col">Created</th>
              <th scope="col">Resolved</th>
            </tr>
          </thead>
          <tbody>
            @for (p of points(); track p.date) {
              <tr>
                <td>{{ p.date }}</td>
                <td>{{ p.created }}</td>
                <td>{{ p.resolved }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    } @else {
      <div class="trend-wrap">
        <svg
          [attr.viewBox]="'0 0 ' + width + ' ' + height"
          role="img"
          class="trend-svg"
          [attr.aria-label]="summary()"
          (mousemove)="hover($event)"
          (mouseleave)="active.set(null)"
        >
          @for (tick of ticks(); track tick) {
            <line
              class="grid"
              [attr.x1]="pad.left"
              [attr.x2]="width - pad.right"
              [attr.y1]="y(tick)"
              [attr.y2]="y(tick)"
            />
            <text class="axis" [attr.x]="pad.left - 6" [attr.y]="y(tick) + 4" text-anchor="end">
              {{ tick }}
            </text>
          }
          @for (p of points(); track p.date; let i = $index) {
            @if (i % labelEvery() === 0) {
              <text class="axis" [attr.x]="x(i)" [attr.y]="height - 6" text-anchor="middle">
                {{ p.date.slice(5) }}
              </text>
            }
          }
          <polyline class="line line-1" [attr.points]="path('created')" />
          <polyline class="line line-2" [attr.points]="path('resolved')" />
          @if (points().length > 0) {
            <text
              class="end-label"
              [attr.x]="x(points().length - 1) + 8"
              [attr.y]="y(last().created) + 4"
            >
              {{ last().created }}
            </text>
            <text
              class="end-label"
              [attr.x]="x(points().length - 1) + 8"
              [attr.y]="y(last().resolved) + 14"
            >
              {{ last().resolved }}
            </text>
          }
          @if (active(); as i) {
            <line
              class="crosshair"
              [attr.x1]="x(i - 1)"
              [attr.x2]="x(i - 1)"
              [attr.y1]="pad.top"
              [attr.y2]="height - pad.bottom"
            />
            <circle
              class="dot dot-1"
              [attr.cx]="x(i - 1)"
              [attr.cy]="y(points()[i - 1].created)"
              r="4"
            />
            <circle
              class="dot dot-2"
              [attr.cx]="x(i - 1)"
              [attr.cy]="y(points()[i - 1].resolved)"
              r="4"
            />
          }
        </svg>
        @if (active(); as i) {
          <div class="chart-tooltip" [style.left.%]="(x(i - 1) / width) * 100" role="status">
            <strong>{{ points()[i - 1].date }}</strong><br />
            Created {{ points()[i - 1].created }} · Resolved {{ points()[i - 1].resolved }}
          </div>
        }
      </div>
    }
  `,
})
export class TrendChart {
  readonly points = input.required<TrendPoint[]>();

  protected readonly width = WIDTH;
  protected readonly height = HEIGHT;
  protected readonly pad = PAD;
  protected readonly showTable = signal(false);
  /** 1-based index of the hovered day (0/null = none, so the template can use it as a truthy value). */
  protected readonly active = signal<number | null>(null);

  private readonly max = computed(() => {
    const m = Math.max(0, ...this.points().flatMap((p) => [p.created, p.resolved]));
    return Math.max(4, Math.ceil(m / 4) * 4);
  });
  protected readonly ticks = computed(() => [0, 1, 2, 3, 4].map((i) => (this.max() / 4) * i));
  protected readonly last = computed(() => this.points()[this.points().length - 1]);
  protected readonly labelEvery = computed(() => Math.max(1, Math.ceil(this.points().length / 7)));
  protected readonly summary = computed(() => {
    const created = this.points().reduce((s, p) => s + p.created, 0);
    const resolved = this.points().reduce((s, p) => s + p.resolved, 0);
    return `Over ${this.points().length} days: ${created} tickets created, ${resolved} resolved.`;
  });

  protected x(i: number): number {
    const n = Math.max(1, this.points().length - 1);
    return PAD.left + (i / n) * (WIDTH - PAD.left - PAD.right);
  }

  protected y(v: number): number {
    return PAD.top + (1 - v / this.max()) * (HEIGHT - PAD.top - PAD.bottom);
  }

  protected path(key: 'created' | 'resolved'): string {
    return this.points()
      .map((p, i) => `${this.x(i)},${this.y(p[key])}`)
      .join(' ');
  }

  protected hover(event: MouseEvent): void {
    const svg = event.currentTarget as SVGSVGElement;
    const rect = svg.getBoundingClientRect();
    if (rect.width === 0 || this.points().length === 0) {
      return;
    }
    const vx = ((event.clientX - rect.left) / rect.width) * WIDTH;
    const n = Math.max(1, this.points().length - 1);
    const i = Math.round(((vx - PAD.left) / (WIDTH - PAD.left - PAD.right)) * n);
    this.active.set(Math.min(this.points().length - 1, Math.max(0, i)) + 1);
  }
}
