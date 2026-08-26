import * as React from "react"
import { cn } from "@/lib/utils"
import { ChevronLeft, ChevronRight, MoreHorizontal } from "lucide-react"
import { Button } from "./button"

interface Column<T> {
  key: string
  header: string
  render?: (row: T) => React.ReactNode
  className?: string
}

interface DataTableProps<T> {
  columns: Column<T>[]
  data: T[]
  keyExtractor: (row: T) => string
  onRowClick?: (row: T) => void
  emptyMessage?: string
  isLoading?: boolean
  tableWrapperClassName?: string
  pagination?: {
    // Support both 0-indexed and 1-indexed page
    page: number
    size?: number
    totalItems?: number // new format
    totalPages?: number // new format
    // Legacy support
    pageSize?: number // alias for size (1-indexed)
    total?: number // alias for totalItems (1-indexed)
    onPageChange: (page: number) => void
  }
  actions?: {
    label: string
    onClick: (row: T) => void
    variant?: "default" | "destructive" | "outline"
  }[]
}

export function DataTable<T>({
  columns,
  data,
  keyExtractor,
  onRowClick,
  emptyMessage = "Không có dữ liệu",
  pagination,
  actions,
  tableWrapperClassName,
}: DataTableProps<T>) {
  const safeData = data ?? []
  // Handle both old format (1-indexed: pageSize, total) and new format (0-indexed: size, totalItems)
  const pageSize = pagination?.size ?? pagination?.pageSize ?? 20
  const totalItems = pagination?.totalItems ?? pagination?.total ?? 0
  const currentPage = pagination?.page ?? 1
  const totalPageCount = pagination?.totalPages ?? Math.ceil(totalItems / pageSize)

  const isOneIndexed = pagination && !('totalItems' in pagination) && !('totalPages' in pagination)
  const displayPage = isOneIndexed ? currentPage : currentPage + 1
  const adjustedTotalPages = isOneIndexed
    ? Math.ceil(totalItems / pageSize)
    : totalPageCount

  return (
    <div className="rounded-xl border border-[var(--border)] bg-[var(--card)] overflow-hidden">
      <div className={cn("overflow-x-auto", tableWrapperClassName)}>
        <table className="w-full">
          <thead>
            <tr className="border-b border-[var(--border)] bg-[var(--muted)]/50">
              {columns.map((column) => (
                <th
                  key={column.key}
                  className={cn(
                    "px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-[var(--muted-foreground)]",
                    column.className
                  )}
                >
                  {column.header}
                </th>
              ))}
              {actions && actions.length > 0 && (
                <th className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wider text-[var(--muted-foreground)]">
                  Thao tác
                </th>
              )}
            </tr>
          </thead>
          <tbody className="divide-y divide-[var(--border)]">
            {safeData.length === 0 ? (
              <tr>
                <td
                  colSpan={columns.length + (actions ? 1 : 0)}
                  className="px-4 py-12 text-center text-[var(--muted-foreground)]"
                >
                  {emptyMessage}
                </td>
              </tr>
            ) : (
              safeData.map((row) => (
                <tr
                  key={keyExtractor(row)}
                  className={cn(
                    "transition-colors hover:bg-[var(--muted)]/30",
                    onRowClick && "cursor-pointer"
                  )}
                  onClick={() => onRowClick?.(row)}
                >
                  {columns.map((column) => (
                    <td
                      key={column.key}
                      className={cn(
                        "px-4 py-3 text-sm text-[var(--foreground)]",
                        column.className
                      )}
                    >
                      {column.render
                        ? column.render(row)
                        : String((row as Record<string, unknown>)[column.key] ?? "")}
                    </td>
                  ))}
                  {actions && actions.length > 0 && (
                    <td className="px-4 py-3 text-right">
                      <div className="flex items-center justify-end gap-2">
                        {actions.map((action, index) => (
                          <Button
                            key={index}
                            variant={action.variant || "ghost"}
                            size="sm"
                            onClick={(e) => {
                              e.stopPropagation()
                              action.onClick(row)
                            }}
                          >
                            {action.label}
                          </Button>
                        ))}
                      </div>
                    </td>
                  )}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {pagination && adjustedTotalPages > 1 && (
        <div className="flex items-center justify-between border-t border-[var(--border)] px-4 py-3">
          <p className="text-sm text-[var(--muted-foreground)]">
            Hiển thị {(isOneIndexed ? displayPage - 1 : displayPage - 1) * pageSize + 1} -{" "}
            {Math.min((isOneIndexed ? displayPage - 1 : displayPage - 1 + 1) * pageSize, totalItems)} trong{" "}
            {totalItems} kết quả
          </p>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => pagination.onPageChange(isOneIndexed ? displayPage - 1 : currentPage - 1)}
              disabled={isOneIndexed ? displayPage === 1 : currentPage === 0}
            >
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <span className="text-sm font-medium">
              Trang {displayPage} / {adjustedTotalPages}
            </span>
            <Button
              variant="outline"
              size="sm"
              onClick={() => pagination.onPageChange(isOneIndexed ? displayPage + 1 : currentPage + 1)}
              disabled={isOneIndexed ? displayPage >= adjustedTotalPages : currentPage >= adjustedTotalPages - 1}
            >
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}
