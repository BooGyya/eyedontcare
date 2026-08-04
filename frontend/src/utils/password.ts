/** 비밀번호 정책: 8~16자, 영문·숫자 각각 1자 이상, 공백 불가. */
export const PASSWORD_POLICY_MESSAGE =
  '비밀번호는 8~16자, 영문과 숫자를 모두 포함하고 공백 없이 입력해야 해요.'

export function isValidPassword(password: string) {
  return (
    password.length >= 8 &&
    password.length <= 16 &&
    /[A-Za-z]/.test(password) &&
    /\d/.test(password) &&
    !/\s/u.test(password)
  )
}
