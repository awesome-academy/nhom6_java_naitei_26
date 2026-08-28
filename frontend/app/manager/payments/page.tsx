import { Suspense } from "react"
import { Loader2 } from "lucide-react"

import { PaymentManagementPage } from "@/components/admin/payments/payment-management-page"

export default function ManagerPaymentsPage() {
  return (
    <Suspense
      fallback={
        <div className="flex min-h-64 items-center justify-center">
          <Loader2 className="animate-spin" />
        </div>
      }
    >
      <PaymentManagementPage portal="/manager" />
    </Suspense>
  )
}
