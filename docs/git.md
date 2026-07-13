# Git 常用命令速查

## 初始化与配置

```bash
git init
git config --global user.name "xxx"
git config --global user.email "xxx@xxx.com"

git config --global color.ui true
git config --global color.status auto
git config --global color.diff auto
git config --global color.branch auto
git config --global color.interactive auto

git config --global --unset http.proxy
```

## 克隆与状态

```bash
git clone git+ssh://git@192.168.53.168/VT.git
git status
```

## 暂存与提交

```bash
git add xyz
git add .

git commit -m "xxx"
git commit --amend -m "xxx"
git commit -am "xxx"
```

## 删除与重命名

```bash
git rm xxx
git rm -r *
git mv README README2
```

## 日志与查看

```bash
git log
git log -1
git log -5
git log --stat
git log -p -m

git show dfb02e6e4f2f7b573337763e5c0013802e392818
git show dfb02
git show HEAD
git show HEAD^
git show HEAD~3
git show -s --pretty=raw 2be7fcb476
```

## 标签（Tag）

```bash
git tag
git tag -a v2.0 -m "xxx"
git show v2.0
git log v2.0
git rev-parse v2.0
```

## 差异（Diff）

```bash
git diff
git diff --cached
git diff HEAD^
git diff HEAD -- ./lib
git diff origin/master..master
git diff origin/master..master --stat
```

## 远程（Remote）与同步

```bash
git remote add origin git+ssh://git@192.168.53.168/VT.git

git fetch
git fetch --prune

git pull origin master

git push origin master
git push origin :hotfixes/BJVEP933
git push --tags
```

## 分支（Branch）与检出（Checkout）

```bash
git branch
git branch -a
git branch -r
git branch --contains 50089
git branch --merged
git branch --no-merged
git branch -m master master_copy
git branch -d hotfixes/BJVEP933
git branch -D hotfixes/BJVEP933

git checkout -b master_copy
git checkout -b master master_copy
git checkout features/performance
git checkout --track hotfixes/BJVEP933
git checkout v2.0
git checkout -b devel origin/develop
git checkout -- README
```

## 合并与回退

```bash
git merge origin/master
git cherry-pick ff44785404a8e

git reset --hard HEAD
git revert dfb02e6e4f2f7b573337763e5c0013802e392818
git rebase
```

## 文件与对象

```bash
git ls-files
git ls-tree HEAD
git show-branch
git show-branch --all
git whatchanged
git reflog
git show HEAD@{5}
git show master@{yesterday}
```

## Stash

```bash
git stash
git stash list
git stash show -p stash@{0}
git stash apply stash@{0}
```

## 搜索与维护

```bash
git grep "delete from"
git grep -e "#define" --and -e SORT_DIRENT

git gc
git fsck
```
