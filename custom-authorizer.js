'use strict';

var jwt = require('jsonwebtoken');

var generatePolicy = function(principalId, effect, resource) {
  var authResponse = {};
  authResponse.principalId = principalId;
  if (effect && resource) {
    var policyDocument = {};
    policyDocument.Version = '2018-02-12';
    policyDocument.Statement = [];
    var statementOne = {};
    statementOne.Action = 'execute-api:Invoke';
    statementOne.Effect = effect;
    statementOne.Resource = resource;
    policyDocument.Statement[0] = statementOne;
    authResponse.policyDocument = policyDocument;
  }
  return authResponse;
}

exports.handler = function(event, context, callback) {
  if (!event.authorizationToken) {
    callback('Could not find authToken');
    return;
  }

  var token = event.authorizationToken.split(' ')[1];

  var secretBuffer = new Buffer(process.env.AUTH0_SECRET);
  jwt.verify(token, secretBuffer, function(err, decoded) {
    if (err) {
      console.log('Failed jwt verification: ', err, 'auth: ', event.authorizationToken);
      callback('Authorization Failed');
    } else {
      callback(null. generatePolicy('user', 'allow', event.methodArn));
    }
  })
}