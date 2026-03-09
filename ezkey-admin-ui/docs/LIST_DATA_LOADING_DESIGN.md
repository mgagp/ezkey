# Admin UI — List data loading: raw vs wrapped (conceptual overview)

This document explains at a **conceptual** level why the Tenants list is the only one that uses a "raw" Orval hook, what that means for the product, and whether it is a concern or technical debt.

---

## 1. What does "raw" vs "wrapped" mean?

### Raw Orval hook

- We call the **hook** that Orval generates from the OpenAPI spec (e.g. `useListTenants()`).
- That hook encapsulates: "call this API endpoint, cache the result under a key chosen by Orval."
- Orval chooses the cache key from the **URL path** (e.g. `['/api/v1/tenants']`). We do not control that key in our code.
- So: **one line of code** to get data, but **we don't own the cache key**.

### Wrapped (our layer)

- We do **not** use the Orval-generated hook for the list.
- We use a **custom hook** (`usePaginatedFromOrval`) that we wrote. It:
  - Uses **TanStack Query** with a **query key we choose** (e.g. `['integrations', filters]`).
  - Calls the **Orval-generated function** (e.g. `search(params)`) to fetch one page of data.
- So: **we own the cache key** and the pagination state; Orval only provides the typed HTTP call.

In short: **raw** = use Orval's hook as-is and accept its cache key. **Wrapped** = use our hook and our cache key, and only use Orval for the actual API call.

---

## 2. Why is Tenants the only "raw" list?

Because the **API design** for tenants is different from the others:

| List        | API behaviour                    | Our choice |
|------------|-----------------------------------|------------|
| Tenants    | Returns the **full list** (no `page`/`size`/`sort`) | Use Orval's hook directly; we paginate and filter **in the browser**. |
| Integrations, Enrollments, Admins, Audit logs | **Server-side pagination** (`page`, `size`, `sort`) | Use `usePaginatedFromOrval`: we send page/size/sort to the API and control the cache key. |

So:

- For **paginated** endpoints we built a single pattern: `usePaginatedFromOrval` + our query key. All those lists behave the same and we can invalidate them with the same key we use for the query.
- For **tenants**, the API returns the full list in one response. There is no "page" to request. So the natural fit was to use the generated hook as-is and do filtering/pagination in the UI. That made Tenants the only list using a raw hook and thus the only one whose cache key is defined by Orval (path-based), not by us.

So the difference is **intentional and API-driven**: one endpoint is "full list", the others are "paginated list". We adapted the UI pattern to each.

---

## 3. Should you be concerned?

**No major concern.**

- **Functionally**: After the auto-refresh fix, the tenants list refreshes correctly after create/update/activate/deactivate because we invalidate using Orval's key factory (`getListTenantsQueryKey()`).
- **Consistency**: We have two valid patterns:
  - Paginated lists → our wrapper + our key.
  - Full-list (tenants) → raw hook + Orval's key, invalidate with the same factory.
- **Risk**: The only pitfall was invalidating with `['tenants']` instead of the real key; that is fixed and documented in AGENTS.md.

So there is no hidden bug or UX issue to worry about.

---

## 4. Is it technical debt?

**Not in a worrying sense.**

- It is **deliberate** given the API: tenants = full list, others = paginated. The "raw" choice fits that.
- The **only** downside was that the cache key was not obvious, so we had to use the generated key factory for invalidation. That is now the standard approach and is documented.
- You could call it "asymmetric" (one list different from the others) but not "debt" in the sense of a shortcut that will cause ongoing problems. It is a one-off difference that is understood and handled.

---

## 5. Could we unify and simplify?

**Options:**

1. **Keep as-is (current state)**  
   - Tenants: raw `useListTenants` + invalidate with `getListTenantsQueryKey()`.  
   - Others: `usePaginatedFromOrval` + our keys.  
   - **Pros**: No refactor, behaviour correct, minimal code. **Cons**: Two patterns to remember.

2. **Thin wrapper for tenants**  
   - Create a small hook that uses `useListTenants` but with a **custom query key** (e.g. `['tenants']`) via the hook's options, so invalidation could use `['tenants']` everywhere.  
   - **Pros**: One key style for all lists. **Cons**: Extra layer and we must check that Orval's hook accepts a custom `queryKey`; if not, we'd wrap TanStack Query ourselves and call the generated **function** only.

3. **Change the API**  
   - Add server-side pagination for tenants (e.g. `GET /tenants?page=0&size=20&sort=...`). Then we could use `usePaginatedFromOrval` like the others.  
   - **Pros**: One pattern for every list. **Cons**: API and backend change, likely overkill if the tenant list stays small.

**Recommendation:** Stay with option 1. The asymmetry is small, well understood, and documented. Unifying would add complexity or API work without a clear product gain unless you expect the tenant list to grow very large and need server-side pagination for other reasons.

---

## 6. What you need to remember (product/tech lead level)

- **Raw** = we use Orval's hook as-is; **Orval** decides the cache key (path-based).  
- **Wrapped** = we use our hook and **we** decide the cache key; Orval only does the HTTP call.  
- Tenants is raw **because** the API returns a full list (no pagination), so we didn't use the pagination wrapper.  
- It's **not** technical debt that demands action; we fixed the only real issue (invalidation key) and documented the pattern.  
- Unifying (wrapper or API change) is **possible** but optional; the current design is intentional and sufficient.
