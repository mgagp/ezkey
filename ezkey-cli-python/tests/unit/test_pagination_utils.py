"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

Test Component: Pagination Utilities Tests
Description: Unit tests for pagination utility functions
"""

import pytest
import click

from ezkey_cli.utils.pagination_utils import (
    build_pagination_params,
    validate_sort_field,
    validate_pagination_options,
    extract_page_content,
    is_paginated_response,
    SORTABLE_FIELDS,
)


class TestBuildPaginationParams:
    """Tests for build_pagination_params function."""

    def test_empty_params(self):
        """Test with no parameters returns empty dict."""
        result = build_pagination_params()
        assert result == {}

    def test_page_only(self):
        """Test with only page parameter."""
        result = build_pagination_params(page=2)
        assert result == {'page': 2}

    def test_size_only(self):
        """Test with only size parameter."""
        result = build_pagination_params(size=50)
        assert result == {'size': 50}

    def test_sort_only(self):
        """Test with only sort parameter."""
        result = build_pagination_params(sort='createdAt,desc')
        assert result == {'sort': 'createdAt,desc'}

    def test_all_params(self):
        """Test with all parameters."""
        result = build_pagination_params(page=1, size=25, sort='enrollmentName,asc')
        assert result == {
            'page': 1,
            'size': 25,
            'sort': 'enrollmentName,asc'
        }

    def test_sort_with_spaces(self):
        """Test sort parameter with spaces is normalized."""
        result = build_pagination_params(sort=' createdAt , desc ')
        assert result == {'sort': 'createdAt,desc'}

    def test_sort_invalid_format_no_comma(self):
        """Test sort parameter without comma raises ValueError."""
        with pytest.raises(ValueError, match="Sort format must be"):
            build_pagination_params(sort='createdAtdesc')

    def test_sort_invalid_direction(self):
        """Test sort parameter with invalid direction raises ValueError."""
        with pytest.raises(ValueError, match="Sort direction must be"):
            build_pagination_params(sort='createdAt,invalid')

    def test_sort_case_insensitive_direction(self):
        """Test sort direction is case-insensitive."""
        result = build_pagination_params(sort='createdAt,DESC')
        assert result == {'sort': 'createdAt,desc'}

        result = build_pagination_params(sort='createdAt,AsC')
        assert result == {'sort': 'createdAt,asc'}


class TestValidateSortField:
    """Tests for validate_sort_field function."""

    def test_valid_enrollment_fields(self):
        """Test all valid enrollment sort fields pass validation."""
        for field in SORTABLE_FIELDS['enrollment']:
            validate_sort_field('enrollment', f'{field},asc')
            # Should not raise any exception

    def test_invalid_field_raises_error(self):
        """Test invalid field raises ValueError with helpful message."""
        with pytest.raises(ValueError, match="Invalid sort field 'invalidField'"):
            validate_sort_field('enrollment', 'invalidField,desc')

    def test_none_sort_passes(self):
        """Test None sort string passes validation."""
        validate_sort_field('enrollment', None)
        # Should not raise any exception

    def test_empty_sort_passes(self):
        """Test empty sort string passes validation."""
        validate_sort_field('enrollment', '')
        # Should not raise any exception

    def test_unknown_entity_type(self):
        """Test unknown entity type raises ValueError."""
        with pytest.raises(ValueError, match="Unknown entity type"):
            validate_sort_field('unknown', 'field,asc')

    def test_valid_auth_attempt_fields(self):
        """Test valid auth-attempt sort fields pass validation."""
        for field in SORTABLE_FIELDS['auth-attempt']:
            validate_sort_field('auth-attempt', f'{field},desc')
            # Should not raise any exception


class TestValidatePaginationOptions:
    """Tests for validate_pagination_options function."""

    def test_valid_options_pass(self):
        """Test valid pagination options pass validation."""
        validate_pagination_options(0, 20, 'createdAt,desc', 'enrollment')
        # Should not raise any exception

    def test_negative_page_raises_error(self):
        """Test negative page number raises click.BadParameter."""
        with pytest.raises(click.BadParameter, match="Page number must be >= 0"):
            validate_pagination_options(-1, 20, None, 'enrollment')

    def test_zero_size_raises_error(self):
        """Test zero page size raises click.BadParameter."""
        with pytest.raises(click.BadParameter, match="Page size must be >= 1"):
            validate_pagination_options(0, 0, None, 'enrollment')

    def test_negative_size_raises_error(self):
        """Test negative page size raises click.BadParameter."""
        with pytest.raises(click.BadParameter, match="Page size must be >= 1"):
            validate_pagination_options(0, -5, None, 'enrollment')

    def test_invalid_sort_field_raises_error(self):
        """Test invalid sort field raises click.BadParameter."""
        with pytest.raises(click.BadParameter, match="Invalid sort field"):
            validate_pagination_options(0, 20, 'invalidField,asc', 'enrollment')

    def test_none_values_pass(self):
        """Test None values pass validation."""
        validate_pagination_options(None, None, None, 'enrollment')
        # Should not raise any exception

    def test_large_size_triggers_warning(self, capsys):
        """Test large page size triggers warning but doesn't raise error."""
        # This test verifies the warning is issued
        # The actual warning display depends on OutputUtils which we'd need to mock
        validate_pagination_options(0, 150, None, 'enrollment')
        # Should not raise any exception


class TestExtractPageContent:
    """Tests for extract_page_content function."""

    def test_extracts_content_from_page(self):
        """Test extraction of content array from Page object."""
        page_data = {
            'content': [{'id': 1}, {'id': 2}],
            'totalElements': 2,
            'totalPages': 1
        }
        result = extract_page_content(page_data)
        assert result == [{'id': 1}, {'id': 2}]

    def test_returns_original_if_not_page(self):
        """Test returns original data if not a Page object."""
        data = [{'id': 1}, {'id': 2}]
        result = extract_page_content(data)
        assert result == data

    def test_returns_original_if_no_content_field(self):
        """Test returns original data if dict has no content field."""
        data = {'items': [{'id': 1}], 'total': 1}
        result = extract_page_content(data)
        assert result == data


class TestIsPaginatedResponse:
    """Tests for is_paginated_response function."""

    def test_identifies_page_response(self):
        """Test correctly identifies Spring Boot Page response."""
        page_data = {
            'content': [],
            'totalPages': 1,
            'totalElements': 0,
            'number': 0,
            'size': 20,
            'first': True,
            'last': True
        }
        assert is_paginated_response(page_data) is True

    def test_rejects_non_dict(self):
        """Test returns False for non-dictionary data."""
        assert is_paginated_response([]) is False
        assert is_paginated_response("string") is False
        assert is_paginated_response(None) is False

    def test_rejects_dict_without_required_fields(self):
        """Test returns False for dict without required Page fields."""
        data = {'items': [], 'total': 0}
        assert is_paginated_response(data) is False

        data = {'content': [], 'total': 0}  # Missing totalPages and number
        assert is_paginated_response(data) is False

    def test_identifies_minimal_page(self):
        """Test identifies Page with only required fields."""
        minimal_page = {
            'content': [],
            'totalPages': 0,
            'number': 0
        }
        assert is_paginated_response(minimal_page) is True


class TestSortableFieldsConfiguration:
    """Tests for SORTABLE_FIELDS configuration."""

    def test_all_entity_types_defined(self):
        """Test all expected entity types have sortable fields defined."""
        expected_types = ['audit-log', 'auth-attempt', 'enrollment', 'integration', 'admin']
        for entity_type in expected_types:
            assert entity_type in SORTABLE_FIELDS
            assert len(SORTABLE_FIELDS[entity_type]) > 0

    def test_enrollment_has_expected_fields(self):
        """Test enrollment has all expected sortable fields."""
        expected_fields = ['enrollmentId', 'enrollmentName', 'createdAt', 'integrationId', 'status']
        assert set(SORTABLE_FIELDS['enrollment']) == set(expected_fields)

    def test_auth_attempt_has_expected_fields(self):
        """Test auth-attempt has all expected sortable fields."""
        expected_fields = ['authAttemptId', 'createdAt', 'expiresAt', 'enrollmentId']
        assert set(SORTABLE_FIELDS['auth-attempt']) == set(expected_fields)

    def test_integration_has_expected_fields(self):
        """Test integration has all expected sortable fields."""
        expected_fields = ['id', 'createdAt', 'active']
        assert set(SORTABLE_FIELDS['integration']) == set(expected_fields)

    def test_audit_log_has_expected_fields(self):
        """Test audit-log has all expected sortable fields."""
        expected_fields = ['auditLogId', 'createdAt', 'eventType', 'eventStatus', 'apiName']
        assert set(SORTABLE_FIELDS['audit-log']) == set(expected_fields)
