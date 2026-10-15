(() => {
  const search = document.querySelector('#__search');
  const drawer = document.querySelector('#__drawer');
  const query = document.querySelector('[data-md-component="search-query"]');
  const sidebar = document.querySelector('.rivet-sidebar');
  const drawerButton = document.querySelector('[aria-controls="rivet-sidebar"]');
  const mobile = matchMedia('(max-width: 959px)');
  const syncDrawer = () => {
    if (sidebar) sidebar.inert = mobile.matches && !drawer.checked;
    drawerButton?.setAttribute('aria-expanded', String(drawer.checked));
  };
  drawer?.addEventListener('change', syncDrawer);
  mobile.addEventListener('change', syncDrawer);
  syncDrawer();
  const currentLink = sidebar?.querySelector('[aria-current="page"]');
  if (currentLink && currentLink.getBoundingClientRect().bottom > sidebar.getBoundingClientRect().bottom - 24) {
    sidebar.scrollTop = currentLink.offsetTop - sidebar.clientHeight / 2;
  }
  // Keep native details semantics while animating both opening and closing.
  const folds = new WeakMap();
  document.querySelectorAll('.rivet-nav-group > summary').forEach(summary => {
    summary.addEventListener('click', event => {
      if (matchMedia('(prefers-reduced-motion: reduce)').matches) return;
      event.preventDefault();
      const group = summary.parentElement;
      const previous = folds.get(group);
      const opening = previous ? !previous.opening : !group.open;
      const from = group.getBoundingClientRect().height;
      previous?.animation.cancel();
      group.open = true;
      const to = opening ? group.getBoundingClientRect().height : summary.getBoundingClientRect().height;
      group.style.overflow = 'hidden';
      const animation = group.animate([{height: `${from}px`}, {height: `${to}px`}], {
        duration: 250, easing: 'cubic-bezier(.4,0,.2,1)'
      });
      folds.set(group, {animation, opening});
      animation.onfinish = () => {
        group.open = opening;
        group.style.removeProperty('overflow');
        folds.delete(group);
      };
    });
  });
  const menu = document.querySelector('.rivet-mobile-links');
  const closeSearch = () => { search.checked = false; search.dispatchEvent(new Event('change', {bubbles:true})); query?.blur(); };
  document.addEventListener('keydown', event => {
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault(); search.checked = !search.checked; search.dispatchEvent(new Event('change', {bubbles:true}));
      if (search.checked) query?.focus();
    }
    if (event.key === 'Escape') { if (search.checked) closeSearch(); drawer.checked = false; syncDrawer(); if (menu) menu.open = false; }
    if ((event.key === 'Enter' || event.key === ' ') && event.target.matches('label[role="button"]')) { event.preventDefault(); event.target.click(); }
  });
  search?.addEventListener('change', () => { if (search.checked) { drawer.checked = false; syncDrawer(); if (menu) menu.open = false; } });
  document.addEventListener('click', event => { if (menu?.open && !menu.contains(event.target)) menu.open = false; });
  document.querySelectorAll('.rivet-navigation a').forEach(link => link.addEventListener('click', () => { drawer.checked = false; syncDrawer(); }));
  if (!/Mac|iPhone|iPad/.test(navigator.platform)) document.querySelector('.rivet-search-trigger kbd').textContent = 'Ctrl K';
})();
