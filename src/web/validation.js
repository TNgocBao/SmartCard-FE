// Shared form validation. Backend validation remains authoritative.
export function validateFields(specs, values) {
  const errors = {};
  for (const spec of specs || []) {
    const raw = values[spec.name] ?? '';
    const value = String(raw);
    if (!value.trim()) { if (spec.required !== false) errors[spec.name] = `Vui lòng nhập ${spec.label.toLowerCase()}.`; continue; }
    if (value.length > (spec.maxLength || 4096)) errors[spec.name] = `Tối đa ${spec.maxLength || 4096} ký tự.`;
    else if (spec.minLength && value.length < spec.minLength) errors[spec.name] = `Cần ít nhất ${spec.minLength} ký tự.`;
    else if (spec.pattern && !new RegExp(`^(?:${spec.pattern})$`).test(value)) errors[spec.name] = spec.hint || 'Định dạng chưa hợp lệ.';
    else if (spec.type === 'email' && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) errors[spec.name] = 'Địa chỉ email chưa hợp lệ.';
    else if (spec.options && !spec.options.some(o => String(o.value) === value)) errors[spec.name] = 'Vui lòng chọn một giá trị trong danh sách.';
  }
  if (values.confirmPin !== undefined && values.newPin !== values.confirmPin) errors.confirmPin = 'PIN nhập lại chưa khớp.';
  return errors;
}
