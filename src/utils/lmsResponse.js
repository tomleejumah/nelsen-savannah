/** LMS envelope — matches nelsen-savanna lms-api-contract.js */

export function lmsOk(res, data, source = "sqlite", status = 200) {
  return res.status(status).json({
    ok: true,
    source,
    data,
    error: null,
  });
}

export function lmsErr(res, message, status = 400, source = "sqlite") {
  return res.status(status).json({
    ok: false,
    source,
    data: null,
    error: message,
  });
}
