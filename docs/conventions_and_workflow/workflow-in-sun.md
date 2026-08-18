# Workflow in Sun*

## 1. Fork

Press button **"Fork"** on GitHub.

---

## 2. Clone

- Use HTTPS:

```bash
git clone https://github.com/<your-github-account>/<repo-name>.git
```

- Use SSH:

```bash
git clone git@github.com:<your-github-account>/<repo-name>.git
```

---

## 3. Remote add sun

```bash
git remote add sun https://github.com/framgia/<repo-name>
```

---

## 4. Checkout new branch

```bash
git checkout -b new_branch
```

---

## 5. Add / Commit

```bash
git add .
git commit -m "commit message"
```

---

## 6. Rebase master

### 6.1 Checkout master

```bash
git checkout master
```

### 6.2 Pull sun master

```bash
git pull sun master
```

### 6.3 Checkout new_branch

```bash
git checkout new_branch
```

### 6.4 Rebase master

```bash
git rebase master
```

### If conflict

#### 6.4.1 No branch

Sau khi xảy ra conflict, Git đang trong quá trình rebase.

#### 6.4.2 Fix conflict

Fix các file đang bị conflict.

#### 6.4.3 Add

```bash
git add .
```

#### 6.4.4 Continue rebase

```bash
git rebase --continue
```

#### 6.4.5 Push lại branch

```bash
git push origin new_branch -f
```

---

## 7. Push origin new_branch

```bash
git push origin new_branch
```

---

## 8. Create Pull Request

Create **Pull Request** trên GitHub.
