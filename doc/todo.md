
# open

## random thoughts

in this section we keep some observations, which - as is - are not actionable
tasks (bugs, features), but might explain things or provide some context.

a very long time ago we had an issue in prod, becuase devs would leave their
sql worksheet clients (some old pro app compatible with our oracle data
warehouse) open for the night, they wouldn't close connections when leaving for
home after day's work. it wasn't immediately obvious which users/connections
were just "scratchpad" connections, and which were prod, it was not easy to
kill those connections and this might have caused issues. note that even if the
query does not lock rows, we still might have issues with MVCC, e.g. the
client's open cursor might refer to otherwise deleted rows and prevent their GC,
causing fragmentation or capacity issues. e.g. for oracle, depending on the
pctfree settings, we might get row migration, which might slowly lead to performance issues that are otherwise hard to diagnose. it
would be cool if sqls handled those edge cases gracefully: we could use a
stateless conn pool for ad-hoc read-only queries (which should generally be
majority of queries), and we could build a concept of explicit write mode
transaction, which would have a reasonable idle-timeout setting with a sane
default; this way it should be possible to open a transaction, write to it and
(after review) to commit it, but on the other hand the probability of disaster
caused by humans accessing databases is minimized.

note that normally humans shouldn't write to prod directly, but we actually
understand life and don't judge - there are times writes to DBs are
unavoidable, and we want to make them as safe as possible (all things
considered). this also applies to non-prod databases, where a stale open
transaction could cause issues: imagine for example debugging an e2e test,
where open session would break all future executions of the suite, possibly
causing delayed releases etc. not as bad as prod outage, but still annoying.

## features

- connected / not connected state of the worksheet,
- separate main worksheet conn from the helper conn,
- detect and warn if current transaction holds locks,
- detect and warn if current transaction is open too long
- optionally auto rollback broken transactions on PostgreSQL and maybe others,
- add type size to the desc box,
- fix config file detection logic,
- builtin support for ssh tunnels,
- clojure starts slow (even though it's quite fast), add a splash screen in
  pure Java before clojure init,
- show resident set size memory stats in the status bar,
- show mem stats in the conn list window also.

## bugs

-   conn error on "test conn" does not fit the message box
-   mem stats should be visible in the new worksheet immediately if the app is
    running for more than ~3s

# done

- on the conn list box, an indicator if the worksheet is open
- memory stats should be aligned right

