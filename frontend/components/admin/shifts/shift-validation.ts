export function validateShiftTimes(
  startTime: string,
  endTime: string,
  crossesMidnight: boolean
): string | null {
  if (!startTime || !endTime) {
    return "Giờ bắt đầu và giờ kết thúc là bắt buộc."
  }

  if (startTime === endTime) {
    return "Giờ bắt đầu và giờ kết thúc không được trùng nhau."
  }

  if (crossesMidnight && endTime >= startTime) {
    return "Ca qua đêm phải có giờ kết thúc trước giờ bắt đầu."
  }

  if (!crossesMidnight && endTime <= startTime) {
    return "Ca thường phải có giờ kết thúc sau giờ bắt đầu."
  }

  return null
}
