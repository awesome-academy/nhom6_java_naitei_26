"use client"

import { format, parseISO } from "date-fns"
import { vi } from "date-fns/locale"
import {
  Bar,
  BarChart,
  CartesianGrid,
  Line,
  LineChart,
  Pie,
  PieChart,
  XAxis,
  YAxis,
} from "recharts"
import { BarChart3 } from "lucide-react"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart"
import type {
  DailyRevenuePoint,
  MonthlyRevenuePoint,
  SourceRevenueBreakdown,
} from "@/types/revenue"

const revenueChartConfig = {
  revenue: {
    label: "Doanh thu",
    color: "var(--accent)",
  },
} satisfies ChartConfig

const monthlyChartConfig = {
  revenue: {
    label: "Doanh thu",
    color: "var(--success)",
  },
} satisfies ChartConfig

const PIE_COLORS = [
  "var(--accent)",
  "var(--success)",
  "var(--warning)",
  "var(--info)",
  "var(--primary)",
]

interface RevenueChartsProps {
  daily: DailyRevenuePoint[]
  monthly: MonthlyRevenuePoint[]
  bySource: SourceRevenueBreakdown[]
}

export function RevenueCharts({ daily, monthly, bySource }: RevenueChartsProps) {
  const sourceData = bySource.map((source, index) => ({
    ...source,
    sourceKey: `source${index}`,
    fill: PIE_COLORS[index % PIE_COLORS.length],
  }))
  const sourceConfig = Object.fromEntries(
    sourceData.map((source) => [
      source.sourceKey,
      { label: source.sourceName, color: source.fill },
    ])
  ) satisfies ChartConfig

  return (
    <div className="grid gap-6 xl:grid-cols-2">
      <Card>
        <CardHeader>
          <CardTitle>Doanh thu theo ngày</CardTitle>
          <CardDescription>Tổng tiền booking ghi nhận tại ngày check-out.</CardDescription>
        </CardHeader>
        <CardContent>
          {daily.length === 0 ? <EmptyChart /> : (
            <ChartContainer config={revenueChartConfig} className="h-72 w-full">
              <BarChart accessibilityLayer data={daily} margin={{ left: 4, right: 4 }}>
                <CartesianGrid vertical={false} />
                <XAxis
                  dataKey="date"
                  axisLine={false}
                  tickLine={false}
                  tickMargin={10}
                  minTickGap={24}
                  tickFormatter={(value: string) => format(parseISO(value), "dd/MM")}
                />
                <YAxis
                  axisLine={false}
                  tickLine={false}
                  tickMargin={8}
                  width={64}
                  tickFormatter={(value: number) => formatCompactCurrency(value)}
                />
                <ChartTooltip
                  cursor={false}
                  content={(
                    <ChartTooltipContent
                      labelFormatter={(label) => format(parseISO(String(label)), "dd/MM/yyyy", { locale: vi })}
                      formatter={(value) => <TooltipRevenue value={Number(value)} />}
                    />
                  )}
                />
                <Bar dataKey="revenue" fill="var(--color-revenue)" radius={[5, 5, 0, 0]} />
              </BarChart>
            </ChartContainer>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Doanh thu theo tháng</CardTitle>
          <CardDescription>Xu hướng doanh thu trong khoảng ngày đã chọn.</CardDescription>
        </CardHeader>
        <CardContent>
          {monthly.length === 0 ? <EmptyChart /> : (
            <ChartContainer config={monthlyChartConfig} className="h-72 w-full">
              <LineChart accessibilityLayer data={monthly} margin={{ left: 4, right: 12 }}>
                <CartesianGrid vertical={false} />
                <XAxis
                  dataKey="month"
                  axisLine={false}
                  tickLine={false}
                  tickMargin={10}
                  tickFormatter={formatMonth}
                />
                <YAxis
                  axisLine={false}
                  tickLine={false}
                  tickMargin={8}
                  width={64}
                  tickFormatter={(value: number) => formatCompactCurrency(value)}
                />
                <ChartTooltip
                  cursor={false}
                  content={(
                    <ChartTooltipContent
                      labelFormatter={(label) => `Tháng ${formatMonth(String(label))}`}
                      formatter={(value) => <TooltipRevenue value={Number(value)} />}
                    />
                  )}
                />
                <Line
                  dataKey="revenue"
                  type="monotone"
                  stroke="var(--color-revenue)"
                  strokeWidth={3}
                  dot={{ fill: "var(--color-revenue)", r: 4 }}
                  activeDot={{ r: 6 }}
                />
              </LineChart>
            </ChartContainer>
          )}
        </CardContent>
      </Card>

      <Card className="xl:col-span-2">
        <CardHeader>
          <CardTitle>Doanh thu theo nguồn booking</CardTitle>
          <CardDescription>Tỷ trọng doanh thu theo kênh bán trong kỳ.</CardDescription>
        </CardHeader>
        <CardContent className="grid items-center gap-6 lg:grid-cols-[minmax(0,1fr)_18rem]">
          {sourceData.length === 0 ? <EmptyChart /> : (
            <>
              <ChartContainer config={sourceConfig} className="mx-auto h-72 w-full max-w-xl">
                <PieChart accessibilityLayer>
                  <ChartTooltip
                    content={(
                      <ChartTooltipContent
                        hideLabel
                        nameKey="sourceKey"
                        formatter={(value, _name, item) => (
                          <div className="flex min-w-48 items-center justify-between gap-4">
                            <span className="text-muted-foreground">{item.payload.sourceName}</span>
                            <span className="font-mono font-medium tabular-nums">
                              {formatCurrency(Number(value))}
                            </span>
                          </div>
                        )}
                      />
                    )}
                  />
                  <Pie
                    data={sourceData}
                    dataKey="revenue"
                    nameKey="sourceKey"
                    innerRadius={58}
                    outerRadius={100}
                    paddingAngle={2}
                    strokeWidth={2}
                  />
                </PieChart>
              </ChartContainer>
              <div className="space-y-3">
                {sourceData.map((source) => (
                  <div key={source.sourceCode} className="flex items-center justify-between gap-4 text-sm">
                    <div className="flex min-w-0 items-center gap-2">
                      <span className="size-2.5 shrink-0 rounded-full" style={{ backgroundColor: source.fill }} />
                      <span className="truncate text-muted-foreground">{source.sourceName}</span>
                    </div>
                    <span className="font-medium tabular-nums">{formatCurrency(source.revenue)}</span>
                  </div>
                ))}
              </div>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  )
}

function TooltipRevenue({ value }: { value: number }) {
  return (
    <div className="flex min-w-40 items-center justify-between gap-4">
      <span className="text-muted-foreground">Doanh thu</span>
      <span className="font-mono font-medium tabular-nums">{formatCurrency(value)}</span>
    </div>
  )
}

function EmptyChart() {
  return (
    <div className="flex h-72 flex-col items-center justify-center gap-3 rounded-lg border border-dashed text-center">
      <BarChart3 className="size-8 text-muted-foreground" aria-hidden="true" />
      <p className="text-sm text-muted-foreground">Chưa có dữ liệu trong khoảng ngày này.</p>
    </div>
  )
}

function formatMonth(value: string): string {
  const [year, month] = value.split("-")
  return `${month}/${year}`
}

function formatCurrency(value: number): string {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(value)
}

function formatCompactCurrency(value: number): string {
  return new Intl.NumberFormat("vi-VN", {
    notation: "compact",
    maximumFractionDigits: 1,
  }).format(value)
}
