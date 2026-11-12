# Integration Tests - Assessment View API

### Run Tests

Dev environment:
```bash
make int-test-dev
```
Test environment:
```bash
make int-test-test
```a

### Environment Variables

Get full kubectl commands from another member of the team
```bash
export ARNS_ASSESSMENT_VIEW_CLIENT_ID=$(kubectl -n hmpps-assess-risks-and-needs-integrations-dev get secret)
export ARNS_ASSESSMENT_VIEW_CLIENT_SECRET=$(kubectl -n hmpps-assess-risks-and-needs-integrations-dev get secret)
```
