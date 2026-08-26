"use client"

import { useEffect, useState } from "react"
import Link from "next/link"

import { SiteHeader } from "@/components/auth/site-header"
import { BookingPaymentStep } from "@/components/payment/booking-payment-step"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { getMyBookingDetail } from "@/lib/api/booking"
import type { BookingDetail } from "@/types/booking"

export function BookingPaymentPage({ publicId }: { publicId: string }) {
  const [detail, setDetail] = useState<BookingDetail | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let ignore = false

    getMyBookingDetail(publicId)
      .then((response) => {
        if (!ignore) setDetail(response)
      })
      .catch((loadError: unknown) => {
        if (!ignore) {
          setError(loadError instanceof Error ? loadError.message : "Không thể tải booking.")
        }
      })

    return () => {
      ignore = true
    }
  }, [publicId])

  return (
    <div className="min-h-screen bg-[var(--background)]">
      <SiteHeader />
      {detail ? (
        <BookingPaymentStep booking={detail.booking} />
      ) : error ? (
        <main className="mx-auto flex min-h-[70vh] max-w-2xl flex-col items-center justify-center gap-5 px-6 text-center">
          <Alert variant="destructive">
            <AlertTitle>Không thể mở thanh toán</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
          <Button asChild variant="outline">
            <Link href="/profile/bookings">Quay lại đơn đặt phòng</Link>
          </Button>
        </main>
      ) : (
        <main className="mx-auto flex max-w-7xl flex-col gap-6 px-6 py-10 lg:px-10">
          <Skeleton className="h-12 w-2/3" />
          <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_380px]">
            <Skeleton className="h-96 w-full" />
            <Skeleton className="h-80 w-full" />
          </div>
        </main>
      )}
    </div>
  )
}
