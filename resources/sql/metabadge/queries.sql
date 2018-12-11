--name: select-user-badge-by-assertion-url
SELECT id, issued_on, status, deleted FROM user_badge AS ub WHERE ub.assertion_url = :assertion_url ORDER BY ctime DESC

--name: select-completed-metabadges
SELECT umr.metabadge_id, umr.user_badge_id, umr.min_required, umr.name, bc.description, bc.image_file, cc.markdown_text AS criteria_content
FROM user_metabadge_received AS umr
JOIN user_badge AS ub ON (umr.user_badge_id = ub.id)
JOIN badge AS badge ON (badge.id = ub.badge_id)
JOIN badge_badge_content AS bbc ON (bbc.badge_id = badge.id)
JOIN badge_content AS bc ON (bc.id = bbc.badge_content_id AND bc.language_code = badge.default_language_code)
JOIN badge_criteria_content AS bcc ON (bcc.badge_id = badge.id)
JOIN criteria_content AS cc ON (cc.id = bcc.criteria_content_id AND cc.language_code = badge.default_language_code)
JOIN user as u on (u.id = ub.user_id)
WHERE u.id = :user_id
GROUP BY umr.metabadge_id

--name: select-received-required-badges
SELECT umrr.metabadge_id, ub.id, bc.name, bc.image_file, ub.issued_on, ub.status, ub.deleted
FROM user_metabadge_required_received AS umrr
JOIN user_badge AS ub ON (umrr.user_required_badge_id = ub.id)
JOIN badge AS badge ON (badge.id = ub.badge_id)
JOIN badge_badge_content AS bbc ON (bbc.badge_id = badge.id)
JOIN badge_content AS bc ON (bc.id = bbc.badge_content_id AND bc.language_code = badge.default_language_code)
JOIN user as u on (u.id = ub.user_id)
WHERE umrr.metabadge_id = :metabadge_id AND u.id = :user_id
GROUP BY ub.id

--name: select-not-received-required-badges
SELECT name, description, criteria, image_file
FROM factory_metabadge_required
WHERE metabadge_id = :metabadge_id;

--name: select-user-required-badges
SELECT ub.id, ubm.meta_badge_req from user_badge AS ub
JOIN user_badge_metabadge as ubm on (ubm.user_badge_id = ub.id)
JOIN user as u on (u.id = ub.user_id)
where u.id = :user_id
GROUP BY ubm.meta_badge_req

--name: select-all-user-metabadges
SELECT fm.id, fm.min_required, fm.name, fm.description, fm.image_file, fm.criteria
FROM user_badge as ub
JOIN user_metabadge_required_received as umrr on (umrr.user_required_badge_id = ub.id)
JOIN factory_metabadge as fm on (fm.id = umrr.metabadge_id)
JOIN user as u on (u.id = ub.user_id)
WHERE u.id = :user_id
GROUP BY fm.id

--name: select-metabadge-info-from-user-badge
SELECT meta_badge, meta_badge_req FROM user_badge_metabadge AS ubm WHERE ubm.user_badge_id = :id

--name: select-completed-metabadge-by-badge-id
SELECT umr.metabadge_id, umr.user_badge_id, umr.min_required, umr.name, bc.description, bc.image_file, cc.markdown_text AS criteria_content
FROM user_metabadge_received AS umr
JOIN user_badge AS ub ON (umr.user_badge_id = ub.id)
JOIN badge AS badge ON (badge.id = ub.badge_id)
JOIN badge_badge_content AS bbc ON (bbc.badge_id = badge.id)
JOIN badge_content AS bc ON (bc.id = bbc.badge_content_id AND bc.language_code = badge.default_language_code)
JOIN badge_criteria_content AS bcc ON (bcc.badge_id = badge.id)
JOIN criteria_content AS cc ON (cc.id = bcc.criteria_content_id AND cc.language_code = badge.default_language_code)
JOIN user as u on (u.id = ub.user_id)
WHERE u.id = :user_id AND umr.user_badge_id = :user_badge_id
GROUP BY umr.metabadge_id

--name: select-required-metatabadge-by-badge-id
SELECT umrr.metabadge_id FROM user_metabadge_required_received AS umrr
WHERE umrr.user_required_badge_id = :id
GROUP BY umrr.metabadge_id
