#!/bin/bash

ant generate-javadoc

eval `ssh-agent -s`
ssh-add github_deploy_key

cd doc

git init
git remote add javadoc git@github.com:TUDa-BP-11/opendiabetes-uam-heuristik.git
git fetch --depth=1 javadoc gh-pages
git add --all
git commit -m "javadoc"
git merge --no-edit -s ours remotes/javadoc/gh-pages
git push javadoc master:gh-pages
