-- wrk script helpers: per-second response counts, non-2xx count, and a JSON summary line
counts = {}
bad = 0
local threads = {}
function setup(thread) table.insert(threads, thread) end
function response(status, headers, body)
  local s = os.time()
  counts[s] = (counts[s] or 0) + 1
  if status < 200 or status >= 300 then bad = bad + 1 end
end
function done(summary, latency, requests)
  local agg, errs = {}, 0
  for _, t in ipairs(threads) do
    for k, v in pairs(t:get("counts")) do agg[k] = (agg[k] or 0) + v end
    errs = errs + t:get("bad")
  end
  local keys = {}
  for k in pairs(agg) do table.insert(keys, k) end
  table.sort(keys)
  local e = summary.errors
  io.write('JSON{"requests":', summary.requests, ',"duration_us":', summary.duration,
    ',"non2xx":', errs, ',"socket_errors":', e.connect + e.read + e.write + e.timeout,
    ',"lat_mean_us":', latency.mean, ',"lat_max_us":', latency.max, ',"percentiles":[')
  local ps = {1, 5, 10, 25, 50, 75, 90, 95, 99, 99.9, 99.99}
  for i, p in ipairs(ps) do
    io.write(i > 1 and ',' or '', '[', p, ',', latency:percentile(p), ']')
  end
  io.write('],"per_second":[')
  for i, k in ipairs(keys) do io.write(i > 1 and ',' or '', '[', k, ',', agg[k], ']') end
  io.write(']}\n')
end
