
# GH-Pages Branch

This branch contains the GitHub Pages content for
[Gradle Manipulator](https://github.com/project-ncl/gradle-manipulator).
Pages are hosted at <https://project-ncl.github.io/gradle-manipulator/>.

## Theme

The site uses the [`jekyll-theme-slate`](https://github.com/pages-themes/slate)
gem. No theme files live in this branch; the gem is supplied by the GitHub
Pages build environment.

## Local preview

`github-pages` does not resolve on Ruby ≥ 4.0. Use `Gemfile.local` instead:

```
bundle install --gemfile Gemfile.local
BUNDLE_GEMFILE=Gemfile.local bundle exec jekyll serve --watch -V
```

## Deployment

The site is built and deployed automatically by GitHub Pages (legacy build,
source branch `gh-pages`, root folder `/`). No workflow file is needed.
