export const maskedSettingValue = '********';

const sensitiveSettingPattern = /(password|secret|token|pin|user_key|subscription_key|consumer_key|api_key|passkey|hmac)/i;

export function isSensitiveSetting(row) {
  if (!row) {
    return false;
  }

  if (row.sensitive === true || row.sensitive === 'true') {
    return true;
  }

  return sensitiveSettingPattern.test(String(row.name || ''));
}